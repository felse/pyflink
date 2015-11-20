/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.api.java.spatial;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.flink.api.common.io.FileInputFormat;
import org.apache.flink.api.common.io.statistics.BaseStatistics;
import org.apache.flink.core.fs.BlockLocation;
import org.apache.flink.core.fs.FSDataInputStream;
import org.apache.flink.core.fs.FileInputSplit;
import org.apache.flink.core.fs.FileStatus;
import org.apache.flink.core.fs.FileSystem;
import org.apache.flink.core.fs.Path;
import org.apache.flink.core.memory.DataInputView;
import org.apache.flink.core.memory.DataOutputView;
import org.apache.flink.util.StringUtils;

/**
 * An input format to parse ENVI files into Tile objects.
 * Every input file is split into adjacent tiles of the given size.
 * Missing pixels are filled with the missing value specified in the ENVI file.
 * 
 * @author Dennis Schneider <dschneid@informatik.hu-berlin.de>
 */
public class EnviInputFormat<T extends Tile> extends FileInputFormat<T> {
	private static final long serialVersionUID = -6483882465613479436L;
	private static final Logger LOG = LoggerFactory.getLogger(EnviInputFormat.class);

	private int xsize = -1, ysize = -1;
	private Coordinate leftUpperLimit = null, rightLowerLimit = null;
	
	private double pixelWidth = -1.0, pixelHeight = -1.0;
	
	private TileInfo info;
	private EnviTilePosition pos;
	private boolean completeScene;
	
	private int readRecords = 0;
	
	public EnviInputFormat(Path path) {
		super(path);
	} 
	
	@Override
	public FileInputSplit[] createInputSplits(int minNumSplits) throws IOException {
		List<FileStatus> files = this.getFiles();
		final FileSystem fs = this.filePath.getFileSystem();
		
		if(minNumSplits < 1) { minNumSplits = 1; }
		
		List<FileInputSplit> inputSplits = new ArrayList<FileInputSplit>(minNumSplits);
		for (FileStatus file : files) {
			// Read header file:
			FSDataInputStream fdis = fs.open(file.getPath());
			try{
				TileInfo info = new TileInfo(fdis);
				fdis.close();

				// Determine data file associated with this header:
				int interleaveType = info.getInterleave();
				if(interleaveType != 0) {
					throw new RuntimeException("Interleave type " + interleaveType + " unsupported, use bsq.");
				}
				Path dataFile = new Path(this.filePath.getFileSystem().getUri() + file.getPath().toUri().getPath().replaceAll("\\.hdr$", "." + TileInfo.InterleaveTypes.values()[interleaveType]));
				FileStatus dataFileStatus;
				try {
					dataFileStatus = fs.getFileStatus(dataFile);
				} catch(FileNotFoundException e) {
					throw new RuntimeException("Data file " + dataFile + " for header " + file + " not found.", e);
				}

				if(info.getDataType() != TileInfo.DataTypes.INT) {
					throw new RuntimeException("Data type " + info.getDataType().name() + " is unsupported, use INT."
							+ " File: " + file.getPath());
				}
				int data_size = info.getPixelSize();
				int numBands = info.getBands();
				int numRows = info.getLines();
				int numColumns = info.getSamples();
				this.pixelHeight = info.getPixelHeight();
				this.pixelWidth = info.getPixelWidth();
				String filePath = file.getPath().toString();
				int lastIndexOf = filePath.lastIndexOf("/");
				filePath = filePath.substring(lastIndexOf + 1);
				String[] split = filePath.split("_");
				String pathRow = split[0];
				String aqcDate = split[1];

				if(completeScene) {
					// Determine list of FS blocks that contain the given block
					final BlockLocation[] blocks = fs.getFileBlockLocations(dataFileStatus, 0, numRows * numColumns);
					Arrays.sort(blocks);

					//create absolute position that comprises the whole scene
					EnviTilePosition absolutePosition = new EnviTilePosition(-1, 0, numColumns, 0, numRows, this.leftUpperLimit, this.rightLowerLimit, pathRow, aqcDate);

					EnviInputSplit sceneSplit = new EnviInputSplit(inputSplits.size(), dataFile, 0, data_size,
							blocks[0].getHosts(), info,
							absolutePosition);
					inputSplits.add(sceneSplit);
				} else {
					createTiledSplits(fs, inputSplits, file, info, dataFile, dataFileStatus, data_size, numBands, numRows, numColumns, pathRow, aqcDate);
				}

			}catch (RuntimeException ex) {
				LOG.warn(ex.getMessage(), ex);
				continue;
			}

		}

		if (inputSplits.size() < minNumSplits) {
			LOG.warn("WARNING: Too few splits generated (" + inputSplits.size() +"), adding dummy splits.");
			for (int index = files.size(); index < minNumSplits; index++) {
				inputSplits.add(new EnviInputSplit(index, null, 0, 0, null, null, null));
			}
		}

		return inputSplits.toArray(new EnviInputSplit[0]);
	}

	private void createTiledSplits(FileSystem fs, List<FileInputSplit> inputSplits, FileStatus file, TileInfo info, Path dataFile, FileStatus dataFileStatus, int data_size, int numBands, int numRows, int numColumns, String pathRow, String aqcDate) throws IOException {
	/*
	 *  Calculate pixel tile size: The rightmost column and lowest row of tiles may contain empty
	 *  pixels.
	 */
		int xsplits = (numColumns + xsize - 1) / xsize;
		int ysplits = (numRows + ysize - 1) / ysize;

		// Real coordinates of this image + coordinate differences:
		Coordinate upperLeftCorner = info.getUpperLeftCoordinate();

		LOG.info("Splitting " + numColumns + "x" + numRows + " image into " +
				xsplits + "x" + ysplits + " tiles of size " + xsize + "x" + ysize + " for " + numBands + " bands: " + file.getPath());

		for(int band = 0; band < numBands; band++) {
			long bandOffset = band * numRows * numColumns;
			for(int currentYSplit = 0; currentYSplit < ysplits; currentYSplit++) {
				// Calculate pixel coordinate of tile:
				int pystart = currentYSplit *  ysize; // inclusive
				int pynext = (currentYSplit + 1) *  ysize; // EXCLUSIVE

				for(int currentXSplit = 0; currentXSplit < xsplits; currentXSplit++) {
					// Calculate pixel coordinate of tile:
					int pxstart = currentXSplit *  xsize; // inclusive
					int pxnext = ((currentXSplit + 1) % xsplits) *  xsize; // EXCLUSIVE
					int pxnextNoWrapped = (currentXSplit + 1) *  xsize; // EXCLUSIVE

					Coordinate tileUpperLeft = new Coordinate(upperLeftCorner.lon + currentXSplit * xsize * pixelWidth, upperLeftCorner.lat - currentYSplit * ysize * pixelHeight);
					Coordinate tileLowerRight = new Coordinate(tileUpperLeft.lon + (xsize-1) * pixelWidth, tileUpperLeft.lat - (ysize-1) * pixelHeight);

					// Filter this tile if no pixel is contained in the selected region:
					if(this.leftUpperLimit != null && !rectIntersectsLimits(tileUpperLeft, tileLowerRight)) {
						if(LOG.isDebugEnabled()) { LOG.debug("Skipping tile at " + currentXSplit + "x" + currentYSplit + ", coordinates " + tileUpperLeft + " -- " + tileLowerRight); }
						continue;
					}

					// Determine start and end position of the pixel block, considering empty pixels at the right and lower boundary:
					long startPos = bandOffset + 1L * pystart * numColumns + pxstart;
					/*
					 *  Pixel position after last pixel in this tile.
					 *  If the next block is in the same row (pxnext >= pxstart), the next start position is not below this block,
					 *  but in the last row of the current block. Thus, decrement pynext in this case.
					 */
					long nextStartPos = bandOffset + 1L * (pxnext < pxstart ? pynext : pynext - 1) * numColumns + pxnext;
					long numPixels = nextStartPos - startPos;

					if(LOG.isDebugEnabled()) { LOG.debug("Tile " + currentXSplit + "x" + currentYSplit + " startPos: " + startPos +" next: " + nextStartPos); }

					long offset = startPos * data_size;
					long length = numPixels * data_size;

					if(offset + length > dataFileStatus.getLen()) { // Don't read over the end of file
						length = dataFileStatus.getLen() - offset;
					}

					if(LOG.isDebugEnabled()) { LOG.debug("Tile " + currentXSplit + "x" + currentYSplit + " at offset " + offset +" +" + length + " bytes"); }
					// Determine list of FS blocks that contain the given block
					final BlockLocation[] blocks = fs.getFileBlockLocations(dataFileStatus, offset, length);
					Arrays.sort(blocks);

					inputSplits.add(new EnviInputSplit(inputSplits.size(), dataFile, offset, length,
							blocks[0].getHosts(), info,
							new EnviTilePosition(band, pxstart, pxnextNoWrapped, pystart, pynext, tileUpperLeft, tileLowerRight, pathRow, aqcDate)));
				}
			}
		}
	}

	/**
	 * Return true iff the rectangle with the given left upper and lower right points, which is oriented along the latitudinal 
	 * and longitudinal lines, intersects with the limits rectangle, which is oriented the same way.
	 */
	private final boolean rectIntersectsLimits(Coordinate tileUpperLeft,
			Coordinate tileLowerRight) {
		// Rectangles intersect if the pairs of left and right latitudinal or longitudinal lines are not besides each other.
		return !dotPairsDontMix(this.leftUpperLimit.lat, this.rightLowerLimit.lat, tileUpperLeft.lat, tileLowerRight.lat)
				&& !dotPairsDontMix(this.leftUpperLimit.lon, this.rightLowerLimit.lon, tileUpperLeft.lon, tileLowerRight.lon);
	}
	
	/**
	 * Return true iff the four numbers are ordered on a circle with circumference "repeat" such that
	 * the intervals a1-a2 and b1-b2 do not intersect.
	 */
	private static final boolean dotPairsDontMix(double a1, double a2, double b1, double b2) {
		// check for both orders on the circel, clockwise:
		int orderedPairs = 0;
		if(a1 > a2) { orderedPairs++; }
		if(a2 > b1) { orderedPairs++; }
		if(b1 > b2) { orderedPairs++; }
		if(b2 > a1) { orderedPairs++; }
		if(orderedPairs == 3) { return true; }
		
		// counter-clockwise:
		orderedPairs = 0;
		if(a1 < a2) { orderedPairs++; }
		if(a2 < b1) { orderedPairs++; }
		if(b1 < b2) { orderedPairs++; }
		if(b2 < a1) { orderedPairs++; }
		return orderedPairs == 3;
	}

	protected List<FileStatus> getFiles() throws IOException {
		// get all the files that are involved in the splits
		List<FileStatus> files = new ArrayList<FileStatus>();

		final FileSystem fs = this.filePath.getFileSystem();
		final FileStatus pathFile = fs.getFileStatus(this.filePath);

		if (pathFile.isDir()) {
			// input is directory. list all contained files
			final FileStatus[] partials = fs.listStatus(this.filePath);
			for (FileStatus partial : partials) {
				if (!partial.isDir() && partial.getPath().toUri().toString().endsWith(".hdr")) {
					files.add(partial);
				}
			}
		} else {
			files.add(pathFile);
		}
		
		return files;
	}

	@Override
	public SequentialStatistics getStatistics(BaseStatistics cachedStats) {
		final FileBaseStatistics cachedFileStats = (cachedStats != null && cachedStats instanceof FileBaseStatistics) ?
			(FileBaseStatistics) cachedStats : null;

		try {
			final Path filePath = this.filePath;

			// get the filesystem
			final FileSystem fs = FileSystem.get(filePath.toUri());
			final ArrayList<FileStatus> allFiles = new ArrayList<FileStatus>(1);

			// let the file input format deal with the up-to-date check and the basic size
			final FileBaseStatistics stats = getFileStats(cachedFileStats, filePath, fs, allFiles);
			if (stats == null) {
				return null;
			}

			// check whether the file stats are still sequential stats (in that case they are still valid)
			if (stats instanceof SequentialStatistics) {
				return (SequentialStatistics) stats;
			}
			return createStatistics(allFiles, stats);
		} catch (IOException ioex) {
			if (LOG.isWarnEnabled()) {
				LOG.warn(String.format("Could not determine complete statistics for file '%s' due to an I/O error: %s",
					this.filePath, StringUtils.stringifyException(ioex)));
			}
		} catch (Throwable t) {
			if (LOG.isErrorEnabled()) {
				LOG.error(String.format("Unexpected problem while getting the file statistics for file '%s' due to %s",
					this.filePath, StringUtils.stringifyException(t)));
			}
		}
		// no stats available
		return null;
	}

	protected FileInputSplit[] getInputSplits() throws IOException {
		return this.createInputSplits(0);
	}

	/**
	 * Fill in the statistics. The last modification time and the total input size are prefilled.
	 * 
	 * @param files
	 *        The files that are associated with this block input format.
	 * @param stats
	 *        The pre-filled statistics.
	 */
	protected SequentialStatistics createStatistics(List<FileStatus> files, FileBaseStatistics stats)
			throws IOException {
		if (files.isEmpty()) {
			return null;
		}
		final FileSystem fs = this.filePath.getFileSystem();
		long totalCount = 0;
		long totalWidth = 0;
		for (FileStatus file : files) {
			// Read header file:
			FSDataInputStream fdis = fs.open(file.getPath());
			TileInfo info = new TileInfo(fdis);
			fdis.close();
	
			// Calculate the number of splits, as done above:
			int xsplits = (info.getSamples() + xsize - 1) / xsize;
			int ysplits = (info.getLines() + ysize - 1) / ysize;
			
			// Count the tiles and calculate the size of each tile in bytes (virtual size):
			int tileCount = xsplits * ysplits * info.getBands();
			totalCount += tileCount;
			totalWidth += 1L * info.getPixelSize() * tileCount * xsize * ysize;
		}

		final float avgWidth = totalCount == 0 ? 0 : 1.0f * totalWidth / totalCount;
		return new SequentialStatistics(stats.getLastModificationTime(), stats.getTotalInputSize(), avgWidth,
			totalCount);
	}

	private static class SequentialStatistics extends FileBaseStatistics {

		private final long numberOfRecords;

		public SequentialStatistics(long fileModTime, long fileSize, float avgBytesPerRecord, long numberOfRecords) {
			super(fileModTime, fileSize, avgBytesPerRecord);
			this.numberOfRecords = numberOfRecords;
		}

		@Override
		public long getNumberOfRecords() {
			return this.numberOfRecords;
		}
	}

	@Override
	public void open(FileInputSplit split) throws IOException {
		super.open(split);
		
		this.info = ((EnviInputSplit) split).info;
		this.pos = ((EnviInputSplit) split).pos;

		if(LOG.isDebugEnabled()) { LOG.debug("Opened ENVI file " + split.getPath() + " with positions: " + pos); }
		
		this.readRecords = 0;
	}

	@Override
	public boolean reachedEnd() throws IOException {
		return this.readRecords > 0;
	}

	@Override
	public T nextRecord(T record) throws IOException {
		if (this.reachedEnd()) {
			return null;
		}
		
		record = readEnviTile(record);
		this.readRecords++;
		return record;
	}

	private T readEnviTile(T record) throws IOException {
		/*
		 * Determine how many pixels to read from the file.
		 * All remaining pixels are filled with missing values
		 */
		int lineWidth = this.info.getSamples();
		double pixelWidth = this.info.getPixelWidth();
		double pixelHeight = this.info.getPixelHeight();
		int xread = lineWidth - this.pos.xstart;
		if(xread > xsize) { xread = xsize; }
		int yread = this.info.getLines() - this.pos.ystart;
		if(yread > ysize) { yread = ysize; }
		
		record.update(this.info, this.pos.leftUpperCorner, this.pos.rightLowerCorner, xsize, ysize, this.pos.band, this.pos.pathRow, this.pos.aqcDate, pixelWidth, pixelHeight);
		short[] values = record.getS16Tile();
		if(values == null) {
			values = new short[xsize * ysize];
			record.setS16Tile(values);
		}
		short dataIgnoreValue = (short) info.getDataIgnoreValue();
		int data_size = info.getPixelSize();
		
		// Fill pixel array pixel by pixel (please optimize this!):
		int pos = 0;
		
		byte[] buffer = new byte[xread * 2];
		short[] shortBuffer = new short[xread];
		
		for(int y = 0; y < yread; y++) {
			// Seek to the beginning of the current line of real data:
			stream.seek(y * lineWidth * data_size + this.splitStart);
			// Fill in pixels (little endian):
			int read = stream.read(buffer);
			if(read < xread) {
				LOG.warn("Should've read " + xread + " pixel, but read only " + read);
			}
			ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shortBuffer);
			System.arraycopy(shortBuffer, 0, values, pos, xread);
			pos = pos + xread;
			
			// Fill with empty columns:
			for(int x = xread; x < xsize; x++) {
				values[pos++] = dataIgnoreValue;
			}
		}
		// Fill missing rows with empty data:
		while(pos < values.length) {
			values[pos++] = dataIgnoreValue;
		}
		return record;
	}
	
	
	public static final class EnviTilePosition implements Serializable{
		public final int band;
		public final int xstart, xnext;
		public final int ystart, ynext;
		public final Coordinate leftUpperCorner, rightLowerCorner;
		public final String pathRow;
		public final String aqcDate;

		public EnviTilePosition(int band, int xstart, int xnext, int ystart, int ynext, Coordinate leftUpperCorner, Coordinate rightLowerCorner, String pathRow, String aqcDate) {
			this.band = band;
			this.xstart = xstart;
			this.xnext = xnext;
			this.ystart = ystart;
			this.ynext = ynext;
			this.leftUpperCorner = leftUpperCorner;
			this.rightLowerCorner = rightLowerCorner;
			this.pathRow = pathRow;
			this.aqcDate = aqcDate;
		}
		
		@Override
		public String toString() {
			return "x:" + xstart + "--" + xnext + ", y:" + ystart + "--" + ynext + ", left Upper: " + leftUpperCorner + ", rightLower: " + rightLowerCorner + ", band " + band;
		}
	}
	
	public static final class EnviInputSplit extends FileInputSplit implements Serializable{
		private static final long serialVersionUID = -9205048860784884871L;
		public TileInfo info;
		public EnviTilePosition pos;

		public EnviInputSplit() {
			super();
			this.info = null;
			this.pos = null;
		}
		
		public EnviInputSplit(int num, Path file, long start, long length, String[] hosts, TileInfo info, EnviTilePosition pos) {
			super(num, file, start, length, hosts);
			this.info = info;
			this.pos = pos;
		}
		
		@Override
		public void read(DataInputView in) throws IOException {
			this.info = new TileInfo();
			this.info.deserialize(in);
			
			String aqcDate = in.readUTF();
			int band = in.readInt();
			Coordinate leftUpper = new Coordinate();
			leftUpper.deserialize(in);
			String pathRow = in.readUTF();
			Coordinate rightLower = new Coordinate();
			rightLower.deserialize(in);
			int xnext = in.readInt();
			int xstart = in.readInt();
			int ynext = in.readInt();
			int ystart = in.readInt();
			this.pos = new EnviTilePosition(band, xstart, xnext, ystart, ynext, leftUpper, rightLower, pathRow, aqcDate);
			
			super.read(in);
		}
		
		@Override
		public void write(DataOutputView out) throws IOException {
			this.info.serialize(out);
			
			out.writeUTF(this.pos.aqcDate);
			out.writeInt(this.pos.band);
			this.pos.leftUpperCorner.serialize(out);
			out.writeUTF(this.pos.pathRow);
			this.pos.rightLowerCorner.serialize(out);
			out.writeInt(this.pos.xnext);
			out.writeInt(this.pos.xstart);
			out.writeInt(this.pos.ynext);
			out.writeInt(this.pos.ystart);
			
			super.write(out);
		}
	}

	public void setLimitRectangle(Coordinate leftUpperLimit,
			Coordinate rightLowerLimit) {
		this.leftUpperLimit = leftUpperLimit;
		this.rightLowerLimit = rightLowerLimit;
	}

	public void setTileSize(int xpixels, int ypixels) {
		this.xsize = xpixels;
		this.ysize = ypixels;
	}

	public void setCompleteScene(boolean completeScene) {
		this.completeScene = completeScene;
	}

}
