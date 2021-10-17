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

	/** @see #getAddresses() */
	public static final String ADDRESSES = "addresses";

	/** Identifier for the property {@link #getAddresses()} in binary format. */
	public static final int ADDRESSES__ID = 1;

	private final java.util.Map<String, GAInfo> _addresses = new java.util.HashMap<>();

	/**
	 * Creates a {@link Cache} instance.
	 *
	 * @see #create()
	 */
	protected Cache() {
		super();
	}

	public final java.util.Map<String, GAInfo> getAddresses() {
		return _addresses;
	}

	/**
	 * @see #getAddresses()
	 */
	public final Cache setAddresses(java.util.Map<String, GAInfo> value) {
		if (value == null) throw new IllegalArgumentException("Property 'addresses' cannot be null.");
		_addresses.clear();
		_addresses.putAll(value);
		return this;
	}

	/**
	 * Adds a value to the {@link #getAddresses()} map.
	 */
	public final void putAddresse(String key, GAInfo value) {
		if (_addresses.containsKey(key)) {
			throw new IllegalArgumentException("Property 'addresses' already contains a value for key '" + key + "'.");
		}
		_addresses.put(key, value);
	}

	private static java.util.List<String> PROPERTIES = java.util.Collections.unmodifiableList(
		java.util.Arrays.asList(
			ADDRESSES));

	@Override
	public java.util.List<String> properties() {
		return PROPERTIES;
	}

	@Override
	public Object get(String field) {
		switch (field) {
			case ADDRESSES: return getAddresses();
			default: return super.get(field);
		}
	}

	@Override
	public void set(String field, Object value) {
		switch (field) {
			case ADDRESSES: setAddresses((java.util.Map<String, GAInfo>) value); break;
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
		out.name(ADDRESSES);
		out.beginObject();
		for (java.util.Map.Entry<String,GAInfo> entry : getAddresses().entrySet()) {
			out.name(entry.getKey());
			entry.getValue().writeTo(out);
		}
		out.endObject();
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case ADDRESSES: {
				in.beginObject();
				while (in.hasNext()) {
					putAddresse(in.nextName(), GAInfo.readGAInfo(in));
				}
				in.endObject();
				break;
			}
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
		out.name(ADDRESSES__ID);
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
			case ADDRESSES__ID: {
				in.beginArray();
				while (in.hasNext()) {
					in.beginObject();
					String key = "";
					GAInfo value = null;
					while (in.hasNext()) {
						switch (in.nextName()) {
							case 1: key = in.nextString(); break;
							case 2: value = GAInfo.readGAInfo(in); break;
							default: in.skipValue(); break;
						}
					}
					putAddresse(key, value);
					in.endObject();
				}
				in.endArray();
				break;
			}
			default: in.skipValue(); 
		}
	}

}
