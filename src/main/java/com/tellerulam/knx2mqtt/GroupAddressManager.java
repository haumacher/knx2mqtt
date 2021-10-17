package com.tellerulam.knx2mqtt;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

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
}
