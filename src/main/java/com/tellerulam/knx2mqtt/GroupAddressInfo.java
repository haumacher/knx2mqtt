package com.tellerulam.knx2mqtt;

import java.io.Serializable;
import java.util.logging.Logger;

import tuwien.auto.calimero.dptxlator.DPTXlator;
import tuwien.auto.calimero.dptxlator.DPTXlatorBoolean;
import tuwien.auto.calimero.dptxlator.TranslatorTypes;
import tuwien.auto.calimero.exception.KNXException;

public class GroupAddressInfo implements Serializable {
	private static final Logger L = Logger.getLogger(GroupAddressManager.class.getName());

	private static final long serialVersionUID = 1;

	private final String name;

	private final String address;

	private String dpt;

	/*
	 * We do not want this serialized, but recreate it explicitely on loading
	 */
	transient DPTXlator xlator;

	/*
	 * Transient state, also not serialized
	 */
	transient Object lastValue;

	transient long lastValueTimestamp;

	public GroupAddressInfo(String name, String address) {
		this.name = name;
		this.address = address;
	}

	public String getName() {
		return name;
	}

	public String getAddress() {
		return address;
	}

	public String getDpt() {
		return dpt;
	}

	public void setDpt(String dpt) {
		this.dpt = dpt;
	}

	@Override
	public String toString() {
		return "{" + name + "|" + dpt + "}";
	}

	void createTranslator() throws KNXException {
		try {
			xlator = TranslatorTypes.createTranslator(0, dpt);
		} catch (KNXException e) {
			L.warning("WARNING! Unable to create translator for DPT " + dpt + " of " + name
					+ ", using 1-byte-value as a fallback.");
			xlator = TranslatorTypes.createTranslator(0, "5.005");
		}
		xlator.setAppendUnit(false);
	}

	private Object translate(byte[] asdu) {
		xlator.setData(asdu);
		if (xlator instanceof DPTXlatorBoolean) {
			if (((DPTXlatorBoolean) xlator).getValueBoolean())
				return Integer.valueOf(1);
			else
				return Integer.valueOf(0);
		}
		// TODO there must be a less lame method to do this
		String strVal = xlator.getValue();
		try {
			return Integer.valueOf(strVal);
		} catch (NumberFormatException nfe) {
			try {
				return Double.valueOf(strVal);
			} catch (NumberFormatException nfe2) {
				return strVal;
			}
		}
	}

	public Object translateAndStoreValue(byte[] asdu, long now) {
		Object newVal = translate(asdu);
		if (!newVal.equals(lastValue)) {
			lastValue = newVal;
			lastValueTimestamp = now;
		}
		return newVal;
	}

	public String getTextutal() {
		String textual;
		xlator.setAppendUnit(true);
		textual = xlator.getValue();
		xlator.setAppendUnit(false);
		return textual;
	}
}