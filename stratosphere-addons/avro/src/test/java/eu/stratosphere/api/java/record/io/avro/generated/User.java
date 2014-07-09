/***********************************************************************************************************************
 * Copyright (C) 2010-2013 by the Apache Flink project (http://flink.incubator.apache.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 **********************************************************************************************************************/

/**
 * Autogenerated by Avro
 * 
 * DO NOT EDIT DIRECTLY
 */
package eu.stratosphere.api.java.record.io.avro.generated;  
@SuppressWarnings("all")
@org.apache.avro.specific.AvroGenerated
public class User extends org.apache.avro.specific.SpecificRecordBase implements org.apache.avro.specific.SpecificRecord {
  public static final org.apache.avro.Schema SCHEMA$ = new org.apache.avro.Schema.Parser().parse("{\"type\":\"record\",\"name\":\"User\",\"namespace\":\"eu.stratosphere.api.java.record.io.avro.generated\",\"fields\":[{\"name\":\"name\",\"type\":\"string\"},{\"name\":\"favorite_number\",\"type\":[\"int\",\"null\"]},{\"name\":\"favorite_color\",\"type\":[\"string\",\"null\"]},{\"name\":\"type_long_test\",\"type\":[\"long\",\"null\"]},{\"name\":\"type_double_test\",\"type\":[\"double\"]},{\"name\":\"type_null_test\",\"type\":[\"null\"]},{\"name\":\"type_bool_test\",\"type\":[\"boolean\"]},{\"name\":\"type_array_string\",\"type\":{\"type\":\"array\",\"items\":\"string\"}},{\"name\":\"type_array_boolean\",\"type\":{\"type\":\"array\",\"items\":\"boolean\"}},{\"name\":\"type_nullable_array\",\"type\":[\"null\",{\"type\":\"array\",\"items\":\"string\"}],\"default\":null},{\"name\":\"type_enum\",\"type\":{\"type\":\"enum\",\"name\":\"Colors\",\"symbols\":[\"RED\",\"GREEN\",\"BLUE\"]}},{\"name\":\"type_map\",\"type\":{\"type\":\"map\",\"values\":\"long\"}}]}");
  public static org.apache.avro.Schema getClassSchema() { return SCHEMA$; }
  @Deprecated public java.lang.CharSequence name;
  @Deprecated public java.lang.Integer favorite_number;
  @Deprecated public java.lang.CharSequence favorite_color;
  @Deprecated public java.lang.Long type_long_test;
  @Deprecated public java.lang.Object type_double_test;
  @Deprecated public java.lang.Object type_null_test;
  @Deprecated public java.lang.Object type_bool_test;
  @Deprecated public java.util.List<java.lang.CharSequence> type_array_string;
  @Deprecated public java.util.List<java.lang.Boolean> type_array_boolean;
  @Deprecated public java.util.List<java.lang.CharSequence> type_nullable_array;
  @Deprecated public eu.stratosphere.api.java.record.io.avro.generated.Colors type_enum;
  @Deprecated public java.util.Map<java.lang.CharSequence,java.lang.Long> type_map;

  /**
   * Default constructor.  Note that this does not initialize fields
   * to their default values from the schema.  If that is desired then
   * one should use {@link \#newBuilder()}. 
   */
  public User() {}

  /**
   * All-args constructor.
   */
  public User(java.lang.CharSequence name, java.lang.Integer favorite_number, java.lang.CharSequence favorite_color, java.lang.Long type_long_test, java.lang.Object type_double_test, java.lang.Object type_null_test, java.lang.Object type_bool_test, java.util.List<java.lang.CharSequence> type_array_string, java.util.List<java.lang.Boolean> type_array_boolean, java.util.List<java.lang.CharSequence> type_nullable_array, eu.stratosphere.api.java.record.io.avro.generated.Colors type_enum, java.util.Map<java.lang.CharSequence,java.lang.Long> type_map) {
    this.name = name;
    this.favorite_number = favorite_number;
    this.favorite_color = favorite_color;
    this.type_long_test = type_long_test;
    this.type_double_test = type_double_test;
    this.type_null_test = type_null_test;
    this.type_bool_test = type_bool_test;
    this.type_array_string = type_array_string;
    this.type_array_boolean = type_array_boolean;
    this.type_nullable_array = type_nullable_array;
    this.type_enum = type_enum;
    this.type_map = type_map;
  }

  public org.apache.avro.Schema getSchema() { return SCHEMA$; }
  // Used by DatumWriter.  Applications should not call. 
  public java.lang.Object get(int field$) {
    switch (field$) {
    case 0: return name;
    case 1: return favorite_number;
    case 2: return favorite_color;
    case 3: return type_long_test;
    case 4: return type_double_test;
    case 5: return type_null_test;
    case 6: return type_bool_test;
    case 7: return type_array_string;
    case 8: return type_array_boolean;
    case 9: return type_nullable_array;
    case 10: return type_enum;
    case 11: return type_map;
    default: throw new org.apache.avro.AvroRuntimeException("Bad index");
    }
  }
  // Used by DatumReader.  Applications should not call. 
  @SuppressWarnings(value="unchecked")
  public void put(int field$, java.lang.Object value$) {
    switch (field$) {
    case 0: name = (java.lang.CharSequence)value$; break;
    case 1: favorite_number = (java.lang.Integer)value$; break;
    case 2: favorite_color = (java.lang.CharSequence)value$; break;
    case 3: type_long_test = (java.lang.Long)value$; break;
    case 4: type_double_test = (java.lang.Object)value$; break;
    case 5: type_null_test = (java.lang.Object)value$; break;
    case 6: type_bool_test = (java.lang.Object)value$; break;
    case 7: type_array_string = (java.util.List<java.lang.CharSequence>)value$; break;
    case 8: type_array_boolean = (java.util.List<java.lang.Boolean>)value$; break;
    case 9: type_nullable_array = (java.util.List<java.lang.CharSequence>)value$; break;
    case 10: type_enum = (eu.stratosphere.api.java.record.io.avro.generated.Colors)value$; break;
    case 11: type_map = (java.util.Map<java.lang.CharSequence,java.lang.Long>)value$; break;
    default: throw new org.apache.avro.AvroRuntimeException("Bad index");
    }
  }

  /**
   * Gets the value of the 'name' field.
   */
  public java.lang.CharSequence getName() {
    return name;
  }

  /**
   * Sets the value of the 'name' field.
   * @param value the value to set.
   */
  public void setName(java.lang.CharSequence value) {
    this.name = value;
  }

  /**
   * Gets the value of the 'favorite_number' field.
   */
  public java.lang.Integer getFavoriteNumber() {
    return favorite_number;
  }

  /**
   * Sets the value of the 'favorite_number' field.
   * @param value the value to set.
   */
  public void setFavoriteNumber(java.lang.Integer value) {
    this.favorite_number = value;
  }

  /**
   * Gets the value of the 'favorite_color' field.
   */
  public java.lang.CharSequence getFavoriteColor() {
    return favorite_color;
  }

  /**
   * Sets the value of the 'favorite_color' field.
   * @param value the value to set.
   */
  public void setFavoriteColor(java.lang.CharSequence value) {
    this.favorite_color = value;
  }

  /**
   * Gets the value of the 'type_long_test' field.
   */
  public java.lang.Long getTypeLongTest() {
    return type_long_test;
  }

  /**
   * Sets the value of the 'type_long_test' field.
   * @param value the value to set.
   */
  public void setTypeLongTest(java.lang.Long value) {
    this.type_long_test = value;
  }

  /**
   * Gets the value of the 'type_double_test' field.
   */
  public java.lang.Object getTypeDoubleTest() {
    return type_double_test;
  }

  /**
   * Sets the value of the 'type_double_test' field.
   * @param value the value to set.
   */
  public void setTypeDoubleTest(java.lang.Object value) {
    this.type_double_test = value;
  }

  /**
   * Gets the value of the 'type_null_test' field.
   */
  public java.lang.Object getTypeNullTest() {
    return type_null_test;
  }

  /**
   * Sets the value of the 'type_null_test' field.
   * @param value the value to set.
   */
  public void setTypeNullTest(java.lang.Object value) {
    this.type_null_test = value;
  }

  /**
   * Gets the value of the 'type_bool_test' field.
   */
  public java.lang.Object getTypeBoolTest() {
    return type_bool_test;
  }

  /**
   * Sets the value of the 'type_bool_test' field.
   * @param value the value to set.
   */
  public void setTypeBoolTest(java.lang.Object value) {
    this.type_bool_test = value;
  }

  /**
   * Gets the value of the 'type_array_string' field.
   */
  public java.util.List<java.lang.CharSequence> getTypeArrayString() {
    return type_array_string;
  }

  /**
   * Sets the value of the 'type_array_string' field.
   * @param value the value to set.
   */
  public void setTypeArrayString(java.util.List<java.lang.CharSequence> value) {
    this.type_array_string = value;
  }

  /**
   * Gets the value of the 'type_array_boolean' field.
   */
  public java.util.List<java.lang.Boolean> getTypeArrayBoolean() {
    return type_array_boolean;
  }

  /**
   * Sets the value of the 'type_array_boolean' field.
   * @param value the value to set.
   */
  public void setTypeArrayBoolean(java.util.List<java.lang.Boolean> value) {
    this.type_array_boolean = value;
  }

  /**
   * Gets the value of the 'type_nullable_array' field.
   */
  public java.util.List<java.lang.CharSequence> getTypeNullableArray() {
    return type_nullable_array;
  }

  /**
   * Sets the value of the 'type_nullable_array' field.
   * @param value the value to set.
   */
  public void setTypeNullableArray(java.util.List<java.lang.CharSequence> value) {
    this.type_nullable_array = value;
  }

  /**
   * Gets the value of the 'type_enum' field.
   */
  public eu.stratosphere.api.java.record.io.avro.generated.Colors getTypeEnum() {
    return type_enum;
  }

  /**
   * Sets the value of the 'type_enum' field.
   * @param value the value to set.
   */
  public void setTypeEnum(eu.stratosphere.api.java.record.io.avro.generated.Colors value) {
    this.type_enum = value;
  }

  /**
   * Gets the value of the 'type_map' field.
   */
  public java.util.Map<java.lang.CharSequence,java.lang.Long> getTypeMap() {
    return type_map;
  }

  /**
   * Sets the value of the 'type_map' field.
   * @param value the value to set.
   */
  public void setTypeMap(java.util.Map<java.lang.CharSequence,java.lang.Long> value) {
    this.type_map = value;
  }

  /** Creates a new User RecordBuilder */
  public static eu.stratosphere.api.java.record.io.avro.generated.User.Builder newBuilder() {
    return new eu.stratosphere.api.java.record.io.avro.generated.User.Builder();
  }
  
  /** Creates a new User RecordBuilder by copying an existing Builder */
  public static eu.stratosphere.api.java.record.io.avro.generated.User.Builder newBuilder(eu.stratosphere.api.java.record.io.avro.generated.User.Builder other) {
    return new eu.stratosphere.api.java.record.io.avro.generated.User.Builder(other);
  }
  
  /** Creates a new User RecordBuilder by copying an existing User instance */
  public static eu.stratosphere.api.java.record.io.avro.generated.User.Builder newBuilder(eu.stratosphere.api.java.record.io.avro.generated.User other) {
    return new eu.stratosphere.api.java.record.io.avro.generated.User.Builder(other);
  }
  
  /**
   * RecordBuilder for User instances.
   */
  public static class Builder extends org.apache.avro.specific.SpecificRecordBuilderBase<User>
    implements org.apache.avro.data.RecordBuilder<User> {

    private java.lang.CharSequence name;
    private java.lang.Integer favorite_number;
    private java.lang.CharSequence favorite_color;
    private java.lang.Long type_long_test;
    private java.lang.Object type_double_test;
    private java.lang.Object type_null_test;
    private java.lang.Object type_bool_test;
    private java.util.List<java.lang.CharSequence> type_array_string;
    private java.util.List<java.lang.Boolean> type_array_boolean;
    private java.util.List<java.lang.CharSequence> type_nullable_array;
    private eu.stratosphere.api.java.record.io.avro.generated.Colors type_enum;
    private java.util.Map<java.lang.CharSequence,java.lang.Long> type_map;

    /** Creates a new Builder */
    private Builder() {
      super(eu.stratosphere.api.java.record.io.avro.generated.User.SCHEMA$);
    }
    
    /** Creates a Builder by copying an existing Builder */
    private Builder(eu.stratosphere.api.java.record.io.avro.generated.User.Builder other) {
      super(other);
      if (isValidValue(fields()[0], other.name)) {
        this.name = data().deepCopy(fields()[0].schema(), other.name);
        fieldSetFlags()[0] = true;
      }
      if (isValidValue(fields()[1], other.favorite_number)) {
        this.favorite_number = data().deepCopy(fields()[1].schema(), other.favorite_number);
        fieldSetFlags()[1] = true;
      }
      if (isValidValue(fields()[2], other.favorite_color)) {
        this.favorite_color = data().deepCopy(fields()[2].schema(), other.favorite_color);
        fieldSetFlags()[2] = true;
      }
      if (isValidValue(fields()[3], other.type_long_test)) {
        this.type_long_test = data().deepCopy(fields()[3].schema(), other.type_long_test);
        fieldSetFlags()[3] = true;
      }
      if (isValidValue(fields()[4], other.type_double_test)) {
        this.type_double_test = data().deepCopy(fields()[4].schema(), other.type_double_test);
        fieldSetFlags()[4] = true;
      }
      if (isValidValue(fields()[5], other.type_null_test)) {
        this.type_null_test = data().deepCopy(fields()[5].schema(), other.type_null_test);
        fieldSetFlags()[5] = true;
      }
      if (isValidValue(fields()[6], other.type_bool_test)) {
        this.type_bool_test = data().deepCopy(fields()[6].schema(), other.type_bool_test);
        fieldSetFlags()[6] = true;
      }
      if (isValidValue(fields()[7], other.type_array_string)) {
        this.type_array_string = data().deepCopy(fields()[7].schema(), other.type_array_string);
        fieldSetFlags()[7] = true;
      }
      if (isValidValue(fields()[8], other.type_array_boolean)) {
        this.type_array_boolean = data().deepCopy(fields()[8].schema(), other.type_array_boolean);
        fieldSetFlags()[8] = true;
      }
      if (isValidValue(fields()[9], other.type_nullable_array)) {
        this.type_nullable_array = data().deepCopy(fields()[9].schema(), other.type_nullable_array);
        fieldSetFlags()[9] = true;
      }
      if (isValidValue(fields()[10], other.type_enum)) {
        this.type_enum = data().deepCopy(fields()[10].schema(), other.type_enum);
        fieldSetFlags()[10] = true;
      }
      if (isValidValue(fields()[11], other.type_map)) {
        this.type_map = data().deepCopy(fields()[11].schema(), other.type_map);
        fieldSetFlags()[11] = true;
      }
    }
    
    /** Creates a Builder by copying an existing User instance */
    private Builder(eu.stratosphere.api.java.record.io.avro.generated.User other) {
            super(eu.stratosphere.api.java.record.io.avro.generated.User.SCHEMA$);
      if (isValidValue(fields()[0], other.name)) {
        this.name = data().deepCopy(fields()[0].schema(), other.name);
        fieldSetFlags()[0] = true;
      }
      if (isValidValue(fields()[1], other.favorite_number)) {
        this.favorite_number = data().deepCopy(fields()[1].schema(), other.favorite_number);
        fieldSetFlags()[1] = true;
      }
      if (isValidValue(fields()[2], other.favorite_color)) {
        this.favorite_color = data().deepCopy(fields()[2].schema(), other.favorite_color);
        fieldSetFlags()[2] = true;
      }
      if (isValidValue(fields()[3], other.type_long_test)) {
        this.type_long_test = data().deepCopy(fields()[3].schema(), other.type_long_test);
        fieldSetFlags()[3] = true;
      }
      if (isValidValue(fields()[4], other.type_double_test)) {
        this.type_double_test = data().deepCopy(fields()[4].schema(), other.type_double_test);
        fieldSetFlags()[4] = true;
      }
      if (isValidValue(fields()[5], other.type_null_test)) {
        this.type_null_test = data().deepCopy(fields()[5].schema(), other.type_null_test);
        fieldSetFlags()[5] = true;
      }
      if (isValidValue(fields()[6], other.type_bool_test)) {
        this.type_bool_test = data().deepCopy(fields()[6].schema(), other.type_bool_test);
        fieldSetFlags()[6] = true;
      }
      if (isValidValue(fields()[7], other.type_array_string)) {
        this.type_array_string = data().deepCopy(fields()[7].schema(), other.type_array_string);
        fieldSetFlags()[7] = true;
      }
      if (isValidValue(fields()[8], other.type_array_boolean)) {
        this.type_array_boolean = data().deepCopy(fields()[8].schema(), other.type_array_boolean);
        fieldSetFlags()[8] = true;
      }
      if (isValidValue(fields()[9], other.type_nullable_array)) {
        this.type_nullable_array = data().deepCopy(fields()[9].schema(), other.type_nullable_array);
        fieldSetFlags()[9] = true;
      }
      if (isValidValue(fields()[10], other.type_enum)) {
        this.type_enum = data().deepCopy(fields()[10].schema(), other.type_enum);
        fieldSetFlags()[10] = true;
      }
      if (isValidValue(fields()[11], other.type_map)) {
        this.type_map = data().deepCopy(fields()[11].schema(), other.type_map);
        fieldSetFlags()[11] = true;
      }
    }

    /** Gets the value of the 'name' field */
    public java.lang.CharSequence getName() {
      return name;
    }
    
    /** Sets the value of the 'name' field */
    public eu.stratosphere.api.java.record.io.avro.generated.User.Builder setName(java.lang.CharSequence value) {
      validate(fields()[0], value);
      this.name = value;
      fieldSetFlags()[0] = true;
      return this; 
    }
    
    /** Checks whether the 'name' field has been set */
    public boolean hasName() {
      return fieldSetFlags()[0];
    }
    
    /** Clears the value of the 'name' field */
    public eu.stratosphere.api.java.record.io.avro.generated.User.Builder clearName() {
      name = null;
      fieldSetFlags()[0] = false;
      return this;
    }

    /** Gets the value of the 'favorite_number' field */
    public java.lang.Integer getFavoriteNumber() {
      return favorite_number;
    }
    
    /** Sets the value of the 'favorite_number' field */
    public eu.stratosphere.api.java.record.io.avro.generated.User.Builder setFavoriteNumber(java.lang.Integer value) {
      validate(fields()[1], value);
      this.favorite_number = value;
      fieldSetFlags()[1] = true;
      return this; 
    }
    
    /** Checks whether the 'favorite_number' field has been set */
    public boolean hasFavoriteNumber() {
      return fieldSetFlags()[1];
    }
    
    /** Clears the value of the 'favorite_number' field */
    public eu.stratosphere.api.java.record.io.avro.generated.User.Builder clearFavoriteNumber() {
      favorite_number = null;
      fieldSetFlags()[1] = false;
      return this;
    }

    /** Gets the value of the 'favorite_color' field */
    public java.lang.CharSequence getFavoriteColor() {
      return favorite_color;
    }
    
    /** Sets the value of the 'favorite_color' field */
    public eu.stratosphere.api.java.record.io.avro.generated.User.Builder setFavoriteColor(java.lang.CharSequence value) {
      validate(fields()[2], value);
      this.favorite_color = value;
      fieldSetFlags()[2] = true;
      return this; 
    }
    
    /** Checks whether the 'favorite_color' field has been set */
    public boolean hasFavoriteColor() {
      return fieldSetFlags()[2];
    }
    
    /** Clears the value of the 'favorite_color' field */
    public eu.stratosphere.api.java.record.io.avro.generated.User.Builder clearFavoriteColor() {
      favorite_color = null;
      fieldSetFlags()[2] = false;
      return this;
    }

    /** Gets the value of the 'type_long_test' field */
    public java.lang.Long getTypeLongTest() {
      return type_long_test;
    }
    
    /** Sets the value of the 'type_long_test' field */
    public eu.stratosphere.api.java.record.io.avro.generated.User.Builder setTypeLongTest(java.lang.Long value) {
      validate(fields()[3], value);
      this.type_long_test = value;
      fieldSetFlags()[3] = true;
      return this; 
    }
    
    /** Checks whether the 'type_long_test' field has been set */
    public boolean hasTypeLongTest() {
      return fieldSetFlags()[3];
    }
    
    /** Clears the value of the 'type_long_test' field */
    public eu.stratosphere.api.java.record.io.avro.generated.User.Builder clearTypeLongTest() {
      type_long_test = null;
      fieldSetFlags()[3] = false;
      return this;
    }

    /** Gets the value of the 'type_double_test' field */
    public java.lang.Object getTypeDoubleTest() {
      return type_double_test;
    }
    
    /** Sets the value of the 'type_double_test' field */
    public eu.stratosphere.api.java.record.io.avro.generated.User.Builder setTypeDoubleTest(java.lang.Object value) {
      validate(fields()[4], value);
      this.type_double_test = value;
      fieldSetFlags()[4] = true;
      return this; 
    }
    
    /** Checks whether the 'type_double_test' field has been set */
    public boolean hasTypeDoubleTest() {
      return fieldSetFlags()[4];
    }
    
    /** Clears the value of the 'type_double_test' field */
    public eu.stratosphere.api.java.record.io.avro.generated.User.Builder clearTypeDoubleTest() {
      type_double_test = null;
      fieldSetFlags()[4] = false;
      return this;
    }

    /** Gets the value of the 'type_null_test' field */
    public java.lang.Object getTypeNullTest() {
      return type_null_test;
    }
    
    /** Sets the value of the 'type_null_test' field */
    public eu.stratosphere.api.java.record.io.avro.generated.User.Builder setTypeNullTest(java.lang.Object value) {
      validate(fields()[5], value);
      this.type_null_test = value;
      fieldSetFlags()[5] = true;
      return this; 
    }
    
    /** Checks whether the 'type_null_test' field has been set */
    public boolean hasTypeNullTest() {
      return fieldSetFlags()[5];
    }
    
    /** Clears the value of the 'type_null_test' field */
    public eu.stratosphere.api.java.record.io.avro.generated.User.Builder clearTypeNullTest() {
      type_null_test = null;
      fieldSetFlags()[5] = false;
      return this;
    }

    /** Gets the value of the 'type_bool_test' field */
    public java.lang.Object getTypeBoolTest() {
      return type_bool_test;
    }
    
    /** Sets the value of the 'type_bool_test' field */
    public eu.stratosphere.api.java.record.io.avro.generated.User.Builder setTypeBoolTest(java.lang.Object value) {
      validate(fields()[6], value);
      this.type_bool_test = value;
      fieldSetFlags()[6] = true;
      return this; 
    }
    
    /** Checks whether the 'type_bool_test' field has been set */
    public boolean hasTypeBoolTest() {
      return fieldSetFlags()[6];
    }
    
    /** Clears the value of the 'type_bool_test' field */
    public eu.stratosphere.api.java.record.io.avro.generated.User.Builder clearTypeBoolTest() {
      type_bool_test = null;
      fieldSetFlags()[6] = false;
      return this;
    }

    /** Gets the value of the 'type_array_string' field */
    public java.util.List<java.lang.CharSequence> getTypeArrayString() {
      return type_array_string;
    }
    
    /** Sets the value of the 'type_array_string' field */
    public eu.stratosphere.api.java.record.io.avro.generated.User.Builder setTypeArrayString(java.util.List<java.lang.CharSequence> value) {
      validate(fields()[7], value);
      this.type_array_string = value;
      fieldSetFlags()[7] = true;
      return this; 
    }
    
    /** Checks whether the 'type_array_string' field has been set */
    public boolean hasTypeArrayString() {
      return fieldSetFlags()[7];
    }
    
    /** Clears the value of the 'type_array_string' field */
    public eu.stratosphere.api.java.record.io.avro.generated.User.Builder clearTypeArrayString() {
      type_array_string = null;
      fieldSetFlags()[7] = false;
      return this;
    }

    /** Gets the value of the 'type_array_boolean' field */
    public java.util.List<java.lang.Boolean> getTypeArrayBoolean() {
      return type_array_boolean;
    }
    
    /** Sets the value of the 'type_array_boolean' field */
    public eu.stratosphere.api.java.record.io.avro.generated.User.Builder setTypeArrayBoolean(java.util.List<java.lang.Boolean> value) {
      validate(fields()[8], value);
      this.type_array_boolean = value;
      fieldSetFlags()[8] = true;
      return this; 
    }
    
    /** Checks whether the 'type_array_boolean' field has been set */
    public boolean hasTypeArrayBoolean() {
      return fieldSetFlags()[8];
    }
    
    /** Clears the value of the 'type_array_boolean' field */
    public eu.stratosphere.api.java.record.io.avro.generated.User.Builder clearTypeArrayBoolean() {
      type_array_boolean = null;
      fieldSetFlags()[8] = false;
      return this;
    }

    /** Gets the value of the 'type_nullable_array' field */
    public java.util.List<java.lang.CharSequence> getTypeNullableArray() {
      return type_nullable_array;
    }
    
    /** Sets the value of the 'type_nullable_array' field */
    public eu.stratosphere.api.java.record.io.avro.generated.User.Builder setTypeNullableArray(java.util.List<java.lang.CharSequence> value) {
      validate(fields()[9], value);
      this.type_nullable_array = value;
      fieldSetFlags()[9] = true;
      return this; 
    }
    
    /** Checks whether the 'type_nullable_array' field has been set */
    public boolean hasTypeNullableArray() {
      return fieldSetFlags()[9];
    }
    
    /** Clears the value of the 'type_nullable_array' field */
    public eu.stratosphere.api.java.record.io.avro.generated.User.Builder clearTypeNullableArray() {
      type_nullable_array = null;
      fieldSetFlags()[9] = false;
      return this;
    }

    /** Gets the value of the 'type_enum' field */
    public eu.stratosphere.api.java.record.io.avro.generated.Colors getTypeEnum() {
      return type_enum;
    }
    
    /** Sets the value of the 'type_enum' field */
    public eu.stratosphere.api.java.record.io.avro.generated.User.Builder setTypeEnum(eu.stratosphere.api.java.record.io.avro.generated.Colors value) {
      validate(fields()[10], value);
      this.type_enum = value;
      fieldSetFlags()[10] = true;
      return this; 
    }
    
    /** Checks whether the 'type_enum' field has been set */
    public boolean hasTypeEnum() {
      return fieldSetFlags()[10];
    }
    
    /** Clears the value of the 'type_enum' field */
    public eu.stratosphere.api.java.record.io.avro.generated.User.Builder clearTypeEnum() {
      type_enum = null;
      fieldSetFlags()[10] = false;
      return this;
    }

    /** Gets the value of the 'type_map' field */
    public java.util.Map<java.lang.CharSequence,java.lang.Long> getTypeMap() {
      return type_map;
    }
    
    /** Sets the value of the 'type_map' field */
    public eu.stratosphere.api.java.record.io.avro.generated.User.Builder setTypeMap(java.util.Map<java.lang.CharSequence,java.lang.Long> value) {
      validate(fields()[11], value);
      this.type_map = value;
      fieldSetFlags()[11] = true;
      return this; 
    }
    
    /** Checks whether the 'type_map' field has been set */
    public boolean hasTypeMap() {
      return fieldSetFlags()[11];
    }
    
    /** Clears the value of the 'type_map' field */
    public eu.stratosphere.api.java.record.io.avro.generated.User.Builder clearTypeMap() {
      type_map = null;
      fieldSetFlags()[11] = false;
      return this;
    }

    @Override
    public User build() {
      try {
        User record = new User();
        record.name = fieldSetFlags()[0] ? this.name : (java.lang.CharSequence) defaultValue(fields()[0]);
        record.favorite_number = fieldSetFlags()[1] ? this.favorite_number : (java.lang.Integer) defaultValue(fields()[1]);
        record.favorite_color = fieldSetFlags()[2] ? this.favorite_color : (java.lang.CharSequence) defaultValue(fields()[2]);
        record.type_long_test = fieldSetFlags()[3] ? this.type_long_test : (java.lang.Long) defaultValue(fields()[3]);
        record.type_double_test = fieldSetFlags()[4] ? this.type_double_test : (java.lang.Object) defaultValue(fields()[4]);
        record.type_null_test = fieldSetFlags()[5] ? this.type_null_test : (java.lang.Object) defaultValue(fields()[5]);
        record.type_bool_test = fieldSetFlags()[6] ? this.type_bool_test : (java.lang.Object) defaultValue(fields()[6]);
        record.type_array_string = fieldSetFlags()[7] ? this.type_array_string : (java.util.List<java.lang.CharSequence>) defaultValue(fields()[7]);
        record.type_array_boolean = fieldSetFlags()[8] ? this.type_array_boolean : (java.util.List<java.lang.Boolean>) defaultValue(fields()[8]);
        record.type_nullable_array = fieldSetFlags()[9] ? this.type_nullable_array : (java.util.List<java.lang.CharSequence>) defaultValue(fields()[9]);
        record.type_enum = fieldSetFlags()[10] ? this.type_enum : (eu.stratosphere.api.java.record.io.avro.generated.Colors) defaultValue(fields()[10]);
        record.type_map = fieldSetFlags()[11] ? this.type_map : (java.util.Map<java.lang.CharSequence,java.lang.Long>) defaultValue(fields()[11]);
        return record;
      } catch (Exception e) {
        throw new org.apache.avro.AvroRuntimeException(e);
      }
    }
  }
}
