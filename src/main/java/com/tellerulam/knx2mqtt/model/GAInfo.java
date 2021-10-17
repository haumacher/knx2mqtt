package com.tellerulam.knx2mqtt.model;

public class GAInfo extends de.haumacher.msgbuf.data.AbstractReflectiveDataObject implements de.haumacher.msgbuf.binary.BinaryDataObject {

	/**
	 * Creates a {@link GAInfo} instance.
	 */
	public static GAInfo create() {
		return new GAInfo();
	}

	/** Identifier for the {@link GAInfo} type in JSON format. */
	public static final String GAINFO__TYPE = "GAInfo";

	/** @see #getName() */
	public static final String NAME = "name";

	/** @see #getDpt() */
	public static final String DPT = "dpt";

	/** Identifier for the property {@link #getName()} in binary format. */
	public static final int NAME__ID = 1;

	/** Identifier for the property {@link #getDpt()} in binary format. */
	public static final int DPT__ID = 2;

	private String _name = "";

	private String _dpt = "";

	/**
	 * Creates a {@link GAInfo} instance.
	 *
	 * @see #create()
	 */
	protected GAInfo() {
		super();
	}

	public final String getName() {
		return _name;
	}

	/**
	 * @see #getName()
	 */
	public final GAInfo setName(String value) {
		_name = value;
		return this;
	}

	public final String getDpt() {
		return _dpt;
	}

	/**
	 * @see #getDpt()
	 */
	public final GAInfo setDpt(String value) {
		_dpt = value;
		return this;
	}

	private static java.util.List<String> PROPERTIES = java.util.Collections.unmodifiableList(
		java.util.Arrays.asList(
			NAME, 
			DPT));

	@Override
	public java.util.List<String> properties() {
		return PROPERTIES;
	}

	@Override
	public Object get(String field) {
		switch (field) {
			case NAME: return getName();
			case DPT: return getDpt();
			default: return super.get(field);
		}
	}

	@Override
	public void set(String field, Object value) {
		switch (field) {
			case NAME: setName((String) value); break;
			case DPT: setDpt((String) value); break;
		}
	}

	/** Reads a new instance from the given reader. */
	public static GAInfo readGAInfo(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		GAInfo result = new GAInfo();
		in.beginObject();
		result.readFields(in);
		in.endObject();
		return result;
	}

	@Override
	public final void writeTo(de.haumacher.msgbuf.json.JsonWriter out) throws java.io.IOException {
		writeContent(out);
	}

	@Override
	protected void writeFields(de.haumacher.msgbuf.json.JsonWriter out) throws java.io.IOException {
		super.writeFields(out);
		out.name(NAME);
		out.value(getName());
		out.name(DPT);
		out.value(getDpt());
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case NAME: setName(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case DPT: setDpt(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			default: super.readField(in, field);
		}
	}

	@Override
	public final void writeTo(de.haumacher.msgbuf.binary.DataWriter out) throws java.io.IOException {
		out.beginObject();
		writeFields(out);
		out.endObject();
	}

	/**
	 * Serializes all fields of this instance to the given binary output.
	 *
	 * @param out
	 *        The binary output to write to.
	 * @throws java.io.IOException If writing fails.
	 */
	protected void writeFields(de.haumacher.msgbuf.binary.DataWriter out) throws java.io.IOException {
		out.name(NAME__ID);
		out.value(getName());
		out.name(DPT__ID);
		out.value(getDpt());
	}

	/** Reads a new instance from the given reader. */
	public static GAInfo readGAInfo(de.haumacher.msgbuf.binary.DataReader in) throws java.io.IOException {
		in.beginObject();
		GAInfo result = new GAInfo();
		while (in.hasNext()) {
			int field = in.nextName();
			result.readField(in, field);
		}
		in.endObject();
		return result;
	}

	/** Consumes the value for the field with the given ID and assigns its value. */
	protected void readField(de.haumacher.msgbuf.binary.DataReader in, int field) throws java.io.IOException {
		switch (field) {
			case NAME__ID: setName(in.nextString()); break;
			case DPT__ID: setDpt(in.nextString()); break;
			default: in.skipValue(); 
		}
	}

}
