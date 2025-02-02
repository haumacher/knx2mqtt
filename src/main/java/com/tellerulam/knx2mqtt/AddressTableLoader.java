package com.tellerulam.knx2mqtt;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AddressTableLoader {

	private static final Logger L = Logger.getLogger(AddressTableLoader.class.getName());

	private GroupAddressManager _addressManager;

	/**
	 * Creates a {@link AddressTableLoader}.
	 */
	public AddressTableLoader(GroupAddressManager addressManager) {
		_addressManager = addressManager;
	}

	/**
	 * Loads an external address table.
	 */
	public void load(String fileName) {
		try {
			DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
			Document doc = docBuilder.parse(new File(fileName));
			NodeList root = doc.getElementsByTagName("GroupAddress-Export");
			iterateGAElement(root.item(0), "");
			L.info("Read " + _addressManager.size() + " Group Address entries from " + fileName);
		} catch (Exception e) {
			L.log(Level.SEVERE, "Unable to parse Group Address table file " + fileName, e);
			System.exit(1);
		}
	}

	private void iterateGAElement(Node n, String prefix) {
		NodeList nlist = n.getChildNodes();
		for (int ix = 0; ix < nlist.getLength(); ix++) {
			Node sn = nlist.item(ix);
			if ("GroupRange".equals(sn.getNodeName())) {
				String name = ((Element) sn).getAttribute("Name");
				iterateGAElement(sn, prefix + name + "/");
			} else if ("GroupAddress".equals(sn.getNodeName())) {
				String name = prefix + ((Element) sn).getAttribute("Name");
				String addr = ((Element) sn).getAttribute("Address");
				GroupAddressInfo gai = new GroupAddressInfo(name, addr);

				_addressManager.add(gai);
			}
		}
	}

}
