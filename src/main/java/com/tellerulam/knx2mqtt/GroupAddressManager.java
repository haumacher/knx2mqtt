package com.tellerulam.knx2mqtt;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;

import com.tellerulam.knx2mqtt.model.Cache;
import com.tellerulam.knx2mqtt.model.GAInfo;

import de.haumacher.msgbuf.json.JsonReader;
import de.haumacher.msgbuf.json.JsonWriter;
import de.haumacher.msgbuf.server.io.ReaderAdapter;
import de.haumacher.msgbuf.server.io.WriterAdapter;
import tuwien.auto.calimero.exception.KNXException;

public class GroupAddressManager {
	private static final Logger L = Logger.getLogger(GroupAddressManager.class.getName());

	private Map<String, GroupAddressInfo> gaTable = new HashMap<>();

	private Map<String, GroupAddressInfo> gaByName = new HashMap<>();

	/**
	 * Creates a {@link GroupAddressManager}.
	 */
	public GroupAddressManager() {
		super();
	}

	public GroupAddressInfo getGAInfoForAddress(String address) {
		return gaTable.get(address);
	}

	public GroupAddressInfo getGAInfoForName(String name) {
		return gaByName.get(name);
	}

	/**
	 * Number of group address assignments.
	 */
	public int size() {
		return gaTable.size();
	}

	/**
	 * Adds a new {@link GroupAddressInfo}.
	 */
	public void add(GroupAddressInfo gai) {
		gaTable.put(gai.getAddress(), gai);
		gaByName.put(gai.getName(), gai);
	}

	/**
	 * All {@link GroupAddressInfo}s.
	 */
	public Collection<GroupAddressInfo> addresses() {
		return gaTable.values();
	}

	/**
	 * Loads group address information from the given file.
	 */
	public void readFromFile(File cacheFile) throws IOException, KNXException {
		Cache cache;
		try (JsonReader json = new JsonReader(
				new ReaderAdapter(new InputStreamReader(new FileInputStream(cacheFile), "utf-8")))) {
			cache = Cache.readCache(json);
		}
	
		for (Entry<String, GAInfo> entry : cache.getAddresses().entrySet()) {
			GAInfo info = entry.getValue();
			GroupAddressInfo gai = new GroupAddressInfo(info.getName(), entry.getKey());
			gai.setDpt(info.getDpt());
			gai.createTranslator();
	
			add(gai);
		}
	}

	/**
	 * Saves group address information to the given file.
	 */
	public void storeToFile(File cacheFile) throws IOException {
		Cache cache = Cache.create();
		for (GroupAddressInfo info : addresses()) {
			cache.putAddresse(info.getAddress(), GAInfo.create().setName(info.getName()).setDpt(info.getDpt()));
		}
		try (JsonWriter json = new JsonWriter(
				new WriterAdapter(new OutputStreamWriter(new FileOutputStream(cacheFile), "utf-8")))) {
			json.setIndent("\t");
			cache.writeContent(json);
		}
	}
}
