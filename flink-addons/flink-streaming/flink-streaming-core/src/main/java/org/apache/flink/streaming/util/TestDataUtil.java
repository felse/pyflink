/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.flink.streaming.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class TestDataUtil {

	// TODO: Exception handling
	// TODO: check checksum after download
	private static final Log log = LogFactory.getLog(TestDataUtil.class);
	public static final String testDataDir = "src/test/resources/testdata/";
	public static final String testRepoUrl = "http://info.ilab.sztaki.hu/~mbalassi/flink-streaming/testdata/";
	public static final String testChekSumDir = "src/test/resources/testdata_checksum/";

	public static void downloadIfNotExists(String fileName) {

		File file = new File(testDataDir + fileName);
		File checkFile = new File(testChekSumDir + fileName + ".md5");
		String checkSumDesired = new String();
		String checkSumActaul = new String();

		File testDataDirectory = new File(testDataDir);
		testDataDirectory.mkdirs();

		try {
			FileReader fileReader = new FileReader(checkFile);
			BufferedReader bufferedReader = new BufferedReader(fileReader);
			checkSumDesired = bufferedReader.readLine();
			bufferedReader.close();
			fileReader.close();
		} catch (FileNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (IOException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
		}

		if (file.exists()) {
			if (log.isInfoEnabled()) {
				log.info(fileName + " already exists.");
			}
			try {
				checkSumActaul = DigestUtils.md5Hex(FileUtils.readFileToByteArray(file));
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			if (!checkSumActaul.equals(checkSumDesired)) {
				if (log.isInfoEnabled()) {
					log.info("Checksum is incorrect.");
					log.info("Downloading file.");
				}
				download(fileName);
			}
		} else {
			if (log.isInfoEnabled()) {
				log.info("File does not exist.");
				log.info("Downloading file.");
			}
			download(fileName);
		}

	}

	public static void download(String fileName) {
		log.info("downloading " + fileName);

		try {
			URL website = new URL(testRepoUrl + fileName);
			BufferedReader bReader = new BufferedReader(new InputStreamReader(website.openStream()));
			File outFile = new File(testDataDir + fileName);
			BufferedWriter bWriter = new BufferedWriter(new FileWriter(outFile));

			String line;
			while ((line = bReader.readLine()) != null) {
				bWriter.write(line);
				bWriter.newLine();
			}
			bWriter.close();
		} catch (MalformedURLException e1) {
			e1.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
