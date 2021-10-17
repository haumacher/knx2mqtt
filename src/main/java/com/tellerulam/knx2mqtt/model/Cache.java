package com.tellerulam.knx2mqtt.model;

public class Cache extends de.haumacher.msgbuf.data.AbstractReflectiveDataObject implements de.haumacher.msgbuf.binary.BinaryDataObject {

	/**
	 * Creates a {@link Cache} instance.
	 */
	public static Cache create() {
		return new Cache();
	}

	/** Identifier for the {@link Cache} type in JSON format. */
	public static final String CACHE__TYPE = "Cache";

	/** @see #getInfos() */
	public static final String INFOS = "infos";

	/** Identifier for the property {@link #getInfos()} in binary format. */
	public static final int INFOS__ID = 1;

	private final java.util.List<GAInfo> _infos = new java.util.ArrayList<>();

	/**
	 * Creates a {@link Cache} instance.
	 *
	 * @see #create()
	 */
	protected Cache() {
		super();
	}

	public final java.util.List<GAInfo> getInfos() {
		return _infos;
	}

	/**
	 * @see #getInfos()
	 */
	public final Cache setInfos(java.util.List<GAInfo> value) {
		if (value == null) throw new IllegalArgumentException("Property 'infos' cannot be null.");
		_infos.clear();
		_infos.addAll(value);
		return this;
	}

	/**
	 * Adds a value to the {@link #getInfos()} list.
	 */
	public final Cache addInfo(GAInfo value) {
		_infos.add(value);
		return this;
	}

	private static java.util.List<String> PROPERTIES = java.util.Collections.unmodifiableList(
		java.util.Arrays.asList(
			INFOS));

	@Override
	public java.util.List<String> properties() {
		return PROPERTIES;
	}

	@Override
	public Object get(String field) {
		switch (field) {
			case INFOS: return getInfos();
			default: return super.get(field);
		}
	}

	@Override
	public void set(String field, Object value) {
		switch (field) {
			case INFOS: setInfos((java.util.List<GAInfo>) value); break;
		}
	}

	/** Reads a new instance from the given reader. */
	public static Cache readCache(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		Cache result = new Cache();
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
		out.name(INFOS);
		out.beginArray();
		for (GAInfo x : getInfos()) {
			x.writeTo(out);
		}
		out.endArray();
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case INFOS: {
				in.beginArray();
				while (in.hasNext()) {
					addInfo(GAInfo.readGAInfo(in));
				}
				in.endArray();
			}
			break;
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
		out.name(INFOS__ID);
		{
			java.util.List<GAInfo> values = getInfos();
			out.beginArray(de.haumacher.msgbuf.binary.DataType.OBJECT, values.size());
			for (GAInfo x : values) {
				x.writeTo(out);
			}
			out.endArray();
		}
	}

	/** Reads a new instance from the given reader. */
	public static Cache readCache(de.haumacher.msgbuf.binary.DataReader in) throws java.io.IOException {
		in.beginObject();
		Cache result = new Cache();
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
			case INFOS__ID: {
				in.beginArray();
				while (in.hasNext()) {
					addInfo(GAInfo.readGAInfo(in));
				}
				in.endArray();
			}
			break;
			default: in.skipValue(); 
		}
	}

}
