package com.tellerulam.knx2mqtt;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import tuwien.auto.calimero.GroupAddress;
import tuwien.auto.calimero.exception.KNXException;

/**
 * Loader for group addresses from an ETS project file.
 */
public class EtsLoader {

	static final Logger L = Logger.getLogger(EtsLoader.class.getName());

	private final GroupAddressManager _addressManager;

	private Map<String, Map<String, Map<String, String>>> _deviceDescriptionCache;

	private Map<Integer, String> _dptMap;

	private SAXParserFactory _saxFactory;

	/**
	 * Creates a {@link EtsLoader}.
	 *
	 * @param addressManager
	 */
	public EtsLoader(GroupAddressManager addressManager) {
		_addressManager = addressManager;
	}

	/**
	 * Load an ETS4 or ETS5 project file
	 */
	public void load(String fileName) {
		File projectFile = new File(fileName);
		if (!projectFile.exists()) {
			L.severe("ETS4/ETS5 project file " + fileName + " does not exit");
			System.exit(1);
		}
		File cacheFile = new File(fileName + ".json");
		if (cacheFile.exists()) {
			if (cacheFile.lastModified() > projectFile.lastModified()) {
				try {
					_addressManager.readFromFile(cacheFile);

					EtsLoader.L.config("Read group address table from " + cacheFile + ".");
					return;
				} catch (Exception e) {
					L.log(Level.WARNING, "Error reading cache file " + cacheFile + ", ignoring it", e);
				}
			} else {
				L.info("Cache file " + cacheFile + " exists, but project file is newer, ignoring it");
			}
		}

		extractGroupAddressInformation(projectFile);

		try {
			_addressManager.storeToFile(cacheFile);
		} catch (Exception e) {
			L.log(Level.INFO, "Unable to write project cache file " + cacheFile
					+ ". This does not impair functionality, but subsequent startups will not be faster", e);
		}
	}

	/**
	 * Loads the group address information from the given ETS project file.
	 */
	private void extractGroupAddressInformation(File projectFile) {
		long startTime = System.currentTimeMillis();
		try (ZipFile zip = new ZipFile(projectFile)) {
			ZipEntry projectEntry = locateProjectEntry(zip);
			if (projectEntry == null) {
				throw new IllegalArgumentException("Unable to locate 0.xml in project");
			}
			analyzeProjectEntry(zip, projectEntry);

			long totalTime = System.currentTimeMillis() - startTime;
			L.config("Reading group address table took " + totalTime + "ms");
		} catch (Exception e) {
			L.log(Level.SEVERE, "Error reading project file " + projectFile, e);
			System.exit(1);
		}
	}

	/**
	 * Search for a file 0.xml in a folder containing the file project.xml.
	 */
	private ZipEntry locateProjectEntry(ZipFile zip) {
		Enumeration<? extends ZipEntry> entries = zip.entries();
		while (entries.hasMoreElements()) {
			ZipEntry entry = entries.nextElement();
			String path = entry.getName();
			int dirSepIdx = path.lastIndexOf('/');
			if (dirSepIdx < 0) {
				continue;
			}
			String fileName = path.substring(dirSepIdx + 1);
			if (fileName.equalsIgnoreCase("project.xml")) {
				String projDir = path.substring(0, dirSepIdx);
				L.info("Found project directory " + projDir);
				// Now find the project data file
				return zip.getEntry(projDir + "/" + "0.xml");
			}
		}
		return null;
	}

	/**
	 * Find the GroupAddresses and their IDs in the project file <code>0.xml</code>.
	 */
	private void analyzeProjectEntry(ZipFile zip, ZipEntry projectEntry)
			throws ParserConfigurationException, SAXException, IOException, KNXException {
		DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
		docBuilderFactory.setCoalescing(true);
		DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
		Document doc = docBuilder.parse(zip.getInputStream(projectEntry));
		NodeList sendConnections = doc.getElementsByTagName("Send");
		NodeList receiveConnections = doc.getElementsByTagName("Receive");

		// For all elements //GroupAddress, build their qualified group names and read out @Address
		// and @DatapointType if available.
		NodeList groupAddressElements = doc.getElementsByTagName("GroupAddress");
		for (int n = 0; n < groupAddressElements.getLength(); n++) {
			Element groupAddressElement = (Element) groupAddressElements.item(n);

			String name = buildQualifiedGroupName(groupAddressElement);
			String address = groupAddressElement.getAttribute("Address");

			String dpt = nonEmpty(groupAddressElement.getAttribute("DatapointType"));
			if (dpt == null) {
				// We're not lucky. Look into connections of this group address.
				String id = groupAddressElement.getAttribute("Id");
				dpt = dptFromGroupAddressConnections(zip, sendConnections, receiveConnections, id, address, name);
			}

			if (dpt != null) {
				storeGAInfo(address, name, dpt);
			}
		}
	}

	private static String nonEmpty(String value) {
		return value == null ? null : value.isEmpty() ? null : value;
	}

	/**
	 * Resolve the full "path" name of the group by going upwards in the GroupRanges
	 */
	private String buildQualifiedGroupName(Element groupAddressElement) {
		String groupName = null;
		Element ancestorOrSelf = groupAddressElement;
		while (true) {
			String localName = ancestorOrSelf.getAttribute("Name").replace('/', '_').replace(' ', '_');
			if (groupName == null) {
				groupName = localName;
			} else {
				groupName = localName + "/" + groupName;
			}

			ancestorOrSelf = (Element) ancestorOrSelf.getParentNode();
			if (!"GroupRange".equals(ancestorOrSelf.getNodeName())) {
				break;
			}
		}
		return groupName;
	}

	/**
	 * Find out what is connected to this group address
	 */
	private String dptFromGroupAddressConnections(ZipFile zip, NodeList sendConnections, NodeList receiveConnections,
			String groupAddressRefId, String address, String name)
			throws SAXException, IOException, ParserConfigurationException {
		
		List<Element> comObjectInstanceRefs = locateComObjectInstanceRefs(sendConnections, receiveConnections,
				groupAddressRefId);
		
		if (comObjectInstanceRefs.size() == 0) {
			L.info("Group address '" + name + "' (" + formatGroupAddress(address)
					+ ") does not seem to be connected at all, ignoring it");
			return null;
		}

		for (Element comObjectInstanceRef : comObjectInstanceRefs) {
			String dpt = comObjectInstanceRef.getAttribute("DatapointType");
			if (dpt.length() != 0) {
				// We're lucky and someone specified the dpt at the CombObjectInstanceRef.
				return dpt;
			}
		}

		// No luck, dig deeper.
		for (Element comObjectInstanceRef : comObjectInstanceRefs) {
			String refId = comObjectInstanceRef.getAttribute("RefId");
			String dpt = dptFromConnectedComObject(zip, refId, address, name, false);
			if (dpt != null) {
				return dpt;
			}
		}

		// No luck at all, infer datapoint type from data size.
		for (Element comObjectInstanceRef : comObjectInstanceRefs) {
			String refId = comObjectInstanceRef.getAttribute("RefId");
			String dpt = dptFromConnectedComObject(zip, refId, address, name, true);
			if (dpt != null) {
				return dpt;
			}
		}

		L.warning("No DPT found for aroup address '" + name + "' (" + formatGroupAddress(address) + "), ignoring it");
		return null;
	}

	private List<Element> locateComObjectInstanceRefs(NodeList sendConnections, NodeList receiveConnections,
			String groupAddressRefId) {
		ArrayList<Element> result = new ArrayList<>();
		addComObjectInstanceRefs(result, sendConnections, groupAddressRefId);
		addComObjectInstanceRefs(result, receiveConnections, groupAddressRefId);
		return result;
	}

	private void addComObjectInstanceRefs(ArrayList<Element> result, NodeList connections, String groupAddressRefId) {
		for (int n = 0; n < connections.getLength(); n++) {
			Element connection = (Element) connections.item(n);
			if (!groupAddressRefId.equals(connection.getAttribute("GroupAddressRefId"))) {
				continue;
			}

			Element comObjectInstanceRef = (Element) connection.getParentNode().getParentNode();
			if (!"ComObjectInstanceRef".equals(comObjectInstanceRef.getNodeName())) {
				L.warning("Weird project structure -- connection not owned by a ComObjectInstanceRef, but "
						+ comObjectInstanceRef.getNodeName());
				continue;
			}

			result.add(comObjectInstanceRef);
		}
	}

	private String dptFromConnectedComObject(ZipFile zip, String comObjectRefId, String address, String name,
			boolean useObjectSize) throws SAXException, IOException, ParserConfigurationException {
		// We need to look into the device description that defines the com object. Determine the
		// device's filename from the reference ID (e.g. M-0083_A-0014-11-EA36_O-56_R-10112).
		String refIdParts[] = comObjectRefId.split("_");

		// Determine path, e.g. M-0083/M-0083_A-0014-11-EA36.xml
		String devicePath = refIdParts[0] + "/" + refIdParts[0] + "_" + refIdParts[1] + ".xml";
		Map<String, Map<String, String>> dev = lookupDeviceDescription(zip, devicePath);

		Map<String, String> comObjRefProperties = dev.get(comObjectRefId);
		if (comObjRefProperties == null) {
			throw new IllegalArgumentException(
					"Unable to find ComObjectRef with Id " + comObjectRefId + " in " + devicePath);
		}

		// Perhaps the ComObjectRef
		String dpt = dptFromComObject(comObjRefProperties, zip, address, name, useObjectSize);
		if (dpt != null) {
			return dpt;
		}

		String comObjectId = comObjRefProperties.get("RefId");
		Map<String, String> comObjectProperties = dev.get(comObjectId);
		if (comObjectProperties == null) {
			throw new IllegalArgumentException("Unable to find ComObject with Id " + comObjectId + " in " + devicePath);
		}

		return dptFromComObject(comObjectProperties, zip, address, name, useObjectSize);
	}

	private String dptFromComObject(Map<String, String> comObjProperties, ZipFile zip, String address, String name,
			boolean useObjectSize) throws SAXException, IOException, ParserConfigurationException {
		if (useObjectSize) {
			String objectSize = nonEmpty(comObjProperties.get("ObjectSize"));
			if (objectSize == null) {
				return null;
			} else {
				String dpt = inferDPTFromObjectSize(zip, objectSize);
				if (!dpt.startsWith("DPST-1-")) {
					L.warning("Infering DPT for group address '" + name + "' (" + formatGroupAddress(address)
							+ ") by size '" + objectSize
							+ "' to '" + dpt
							+ "'. This may not be what you want, please update your ETS4/ETS project with proper DPT specifications!");
				}
				return dpt;
			}
		} else {
			String dpt = nonEmpty(comObjProperties.get("DatapointType"));
			return dpt;
		}
	}

	private void storeGAInfo(String address, String name, String datapointType) throws KNXException {
		String ga = formatGroupAddress(address);
	
		GroupAddressInfo gai = _addressManager.getGAInfoForAddress(ga);
		if (gai == null) {
			gai = new GroupAddressInfo(name, ga);
			_addressManager.add(gai);
		}
	
		Pattern p = Pattern.compile("DPS?T-([0-9]+)(-([0-9]+))?");
		Matcher m = p.matcher(datapointType);
		if (!m.find())
			throw new IllegalArgumentException("Unparsable DPST '" + datapointType + "'");
		StringBuilder dptBuilder = new StringBuilder();
		dptBuilder.append(m.group(1));
		dptBuilder.append('.');
		String suffix = m.group(3);
		if (suffix == null) {
			dptBuilder.append("001");
		} else {
			int suffixLength = suffix.length();
			while (suffixLength++ < 3)
				dptBuilder.append('0');
			dptBuilder.append(suffix);
		}
		gai.setDpt(dptBuilder.toString());
		gai.createTranslator();
	}

	private String formatGroupAddress(String address) {
		return new GroupAddress(Integer.parseInt(address)).toString();
	}

	private String inferDPTFromObjectSize(ZipFile zip, String objSize)
			throws SAXException, IOException, ParserConfigurationException {
		// Take a guess based on size
		switch (objSize) {
		case "1 Bit":
			return "DPST-1-1";
		case "1 Byte":
			return "DPST-5-1";
		case "2 Bytes":
			return "DPST-9-1";
		default: {
			if (_dptMap == null) {
				_dptMap = loadDptMap(zip);
			}
			String sizeSpec[] = objSize.split(" ");
			int bits = Integer.parseInt(sizeSpec[0]);
			if (sizeSpec[1].startsWith("Byte")) {
				bits *= 8;
			}
			return _dptMap.get(bits);
		}
		}
	}

	private Map<Integer, String> loadDptMap(ZipFile zip)
			throws ParserConfigurationException, SAXException, IOException {
		Map<Integer, String> dptMap = new HashMap<>();
		DefaultHandler gaHandler = new DefaultHandler() {
			private String currentSize;

			@Override
			public void startElement(String uri, String localName, String qName, Attributes attr)
					throws SAXException {
				if ("DatapointType".equals(qName)) {
					currentSize = attr.getValue("SizeInBit");
				} else if ("DatapointSubtype".equals(qName)) {
					if (currentSize != null) {
						dptMap.put(Integer.valueOf(currentSize), attr.getValue("Id"));
						currentSize = null;
					}
				}
			}
		};
		newSaxParser().parse(zip.getInputStream(new ZipEntry("knx_master.xml")), gaHandler);
		return dptMap;
	}

	private Map<String, Map<String, String>> lookupDeviceDescription(ZipFile zip, String filename)
			throws ParserConfigurationException, SAXException, IOException {
		if (_deviceDescriptionCache == null) {
			_deviceDescriptionCache = new HashMap<>();
		} else {
			Map<String, Map<String, String>> cacheEntry = _deviceDescriptionCache.get(filename);
			if (cacheEntry != null) {
				return cacheEntry;
			}
		}

		Map<String, Map<String, String>> attrById = parseDeviceDescription(zip, filename);

		_deviceDescriptionCache.put(filename, attrById);
		return attrById;
	}

	/**
	 * Parses a device definition entry and delivers a mapping of all defined <code>ComObject</code>
	 * and <code>ComObjectRef</code> element IDs to their attribute values.
	 */
	private Map<String, Map<String, String>> parseDeviceDescription(ZipFile zip, String filename)
			throws SAXException, IOException, ParserConfigurationException {
		ZipEntry deviceEntry = zip.getEntry(filename);
		if (deviceEntry == null) {
			throw new IllegalArgumentException("Unable to find device description " + filename);
		}

		final Map<String, Map<String, String>> attrById = new HashMap<>();
		DefaultHandler deviceHandler = new DefaultHandler() {
			@Override
			public void startElement(String uri, String localName, String qName, Attributes attr) throws SAXException {
				if ("ComObjectRef".equals(qName) || "ComObject".equals(qName)) {
					// Store properties.
					Map<String, String> properties = new HashMap<>();
					for (int n = 0; n < attr.getLength(); n++) {
						properties.put(attr.getQName(n), attr.getValue(n));
					}
					attrById.put(properties.get("Id"), properties);
				}
			}
		};
		newSaxParser().parse(zip.getInputStream(deviceEntry), deviceHandler);
		return attrById;
	}

	private SAXParser newSaxParser() throws ParserConfigurationException, SAXException {
		if (_saxFactory == null) {
			_saxFactory = SAXParserFactory.newInstance();
		}
		return _saxFactory.newSAXParser();
	}

}
