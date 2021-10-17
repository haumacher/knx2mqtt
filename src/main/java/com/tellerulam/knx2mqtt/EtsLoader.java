package com.tellerulam.knx2mqtt;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
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

import com.tellerulam.knx2mqtt.model.Cache;
import com.tellerulam.knx2mqtt.model.GAInfo;

import de.haumacher.msgbuf.json.JsonReader;
import de.haumacher.msgbuf.json.JsonWriter;
import de.haumacher.msgbuf.server.io.ReaderAdapter;
import de.haumacher.msgbuf.server.io.WriterAdapter;
import tuwien.auto.calimero.GroupAddress;
import tuwien.auto.calimero.exception.KNXException;

/**
 * Loader for group addresses from an ETS project file.
 */
public class EtsLoader {

	private static final Logger L = Logger.getLogger(EtsLoader.class.getName());

	private final GroupAddressManager _addressManager;

	private Map<String, Map<String, Map<String, String>>> deviceDescriptionCache;

	private Map<Integer, String> dptMap;

	private SAXParserFactory saxFactory;

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
	void loadETS4Project() {
		String gaFile = System.getProperty(PropertyNames.KNX2MQTT_KNX_ETS5PROJECTFILE);
		if (gaFile == null)
			gaFile = System.getProperty(PropertyNames.KNX2MQTT_KNX_ETS4PROJECTFILE);
		if (gaFile == null) {
			L.config("No ETS4/ETS5 project file specified");
			return;
		}
		File projectFile = new File(gaFile);
		if (!projectFile.exists()) {
			L.severe("ETS4/ETS5 project file " + gaFile + " does not exit");
			System.exit(1);
		}
		File cacheFile = new File(gaFile + ".json");
		if (cacheFile.exists()) {
			if (cacheFile.lastModified() > projectFile.lastModified()) {
				try {
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

						_addressManager.add(gai);
					}

					L.config("Read group address table from " + cacheFile + ".");
					return;
				} catch (Exception e) {
					L.log(Level.WARNING, "Error reading cache file " + cacheFile + ", ignoring it", e);
				}
			} else {
				L.info("Cache file " + cacheFile + " exists, but project file is newer, ignoring it");
			}
		}
		long startTime = System.currentTimeMillis();
		try (ZipFile zf = new ZipFile(gaFile)) {
			// Find the project file
			Enumeration<? extends ZipEntry> entries = zf.entries();
			while (entries.hasMoreElements()) {
				ZipEntry ze = entries.nextElement();
				if (ze.getName().toLowerCase().endsWith("project.xml")) {
					String projDir = ze.getName().substring(0, ze.getName().indexOf('/') + 1);
					L.info("Found project directory " + projDir);
					// Now find the project data file
					ZipEntry zep = zf.getEntry(projDir + "0.xml");
					if (zep == null)
						throw new IllegalArgumentException("Unable to locate 0.xml in project");
					processETS4ProjectFile(zf, zep);
					break;
				}
			}
			long totalTime = System.currentTimeMillis() - startTime;
			L.config("Reading group address table took " + totalTime + "ms");
		} catch (Exception e) {
			L.log(Level.SEVERE, "Error reading project file " + gaFile, e);
			System.exit(1);
		}

		Cache cache = Cache.create();
		for (GroupAddressInfo info : _addressManager.addresses()) {
			cache.putAddresse(info.getAddress(), GAInfo.create().setName(info.getName()).setDpt(info.getDpt()));
		}
		try (JsonWriter json = new JsonWriter(
				new WriterAdapter(new OutputStreamWriter(new FileOutputStream(cacheFile), "utf-8")))) {
			json.setIndent("\t");
			cache.writeContent(json);
		} catch (Exception e) {
			L.log(Level.INFO, "Unable to write project cache file " + cacheFile
					+ ". This does not impair functionality, but subsequent startups will not be faster", e);
		}
	}

	/*
	 * First step in parsing: find the GroupAddresses and their IDs
	 */
	private void processETS4ProjectFile(ZipFile zf, ZipEntry zep)
			throws ParserConfigurationException, SAXException, IOException, KNXException {
		DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
		docBuilderFactory.setCoalescing(true);
		DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
		Document doc = docBuilder.parse(zf.getInputStream(zep));
		NodeList gas = doc.getElementsByTagName("GroupAddress");
		NodeList sendConnections = doc.getElementsByTagName("Send");
		NodeList receiveConnections = doc.getElementsByTagName("Receive");
		for (int ix = 0; ix < gas.getLength(); ix++) {
			Element e = (Element) gas.item(ix);
			// Resolve the full "path" name of the group by going upwards in the
			// GroupRanges
			String name = null;
			for (Element pe = e;;) {
				if (name == null)
					name = pe.getAttribute("Name");
				else
					name = pe.getAttribute("Name") + "/" + name;

				pe = (Element) pe.getParentNode();
				if (!"GroupRange".equals(pe.getNodeName()))
					break;
			}

			String address = e.getAttribute("Address");

			// If we're lucky, the DPT is already specified here
			String dpt = e.getAttribute("DatapointType");
			if (dpt.length() != 0) {
				storeGAInfo(address, name, dpt);
				continue;
			}

			// We're not lucky. Look into the connections
			processETS4GroupAddressConnections(zf, doc, sendConnections, receiveConnections, e.getAttribute("Id"),
					address, name);
		}
	}

	private void storeGAInfo(String address, String name, String datapointType) throws KNXException {
		String ga = new GroupAddress(Integer.parseInt(address)).toString();

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

	/**
	 * Find out what is connected to this group address
	 */
	private void processETS4GroupAddressConnections(ZipFile zf, Document doc, NodeList sendConnections,
			NodeList receiveConnections, String id, String address, String name)
			throws SAXException, IOException, ParserConfigurationException, KNXException {
		boolean foundConnection = false;
		for (int attempt = 0; attempt < 4; attempt++) {
			// We can give up early if we didn't find a connection at all
			if (attempt == 2 && !foundConnection)
				break;
			NodeList connectors = ((attempt & 1) == 0) ? receiveConnections : sendConnections;
			boolean useObjectSize = (attempt & 2) != 0;
			for (int ix = 0; ix < connectors.getLength(); ix++) {
				Element e = (Element) connectors.item(ix);
				if (id.equals(e.getAttribute("GroupAddressRefId"))) {
					Element pe = (Element) e.getParentNode().getParentNode();
					if (!"ComObjectInstanceRef".equals(pe.getNodeName())) {
						L.warning("Weird project structure -- connection not owned by a ComObjectInstanceRef, but "
								+ pe.getNodeName());
						continue;
					}
					foundConnection = true;

					/*
					 * Perhaps we're lucky and someone specified it in the CombObjectInstanceRef?
					 */
					String dpt = pe.getAttribute("DatapointType");
					if (dpt.length() != 0) {
						storeGAInfo(address, name, dpt);
						return;
					}
					/* No luck, no luck. Dig deeper */
					if (processETS4GroupConnection(zf, pe.getAttribute("RefId"), id, address, name, useObjectSize))
						return;
				}
			}
		}
		if (!foundConnection)
			L.info("Group " + id + "/" + address + "/" + name
					+ " does not seem to be connected to anywhere, ignoring it");
		else
			throw new IllegalArgumentException(
					"Unable to determine datapoint type for " + id + "/" + address + "/" + name);
	}

	private boolean processETS4GroupConnection(ZipFile zf, String refId, String id, String address, String name,
			boolean useObjectSize) throws SAXException, IOException, ParserConfigurationException, KNXException {
		// Right, we need to look into the device description. Determine it's
		// filename
		String refIdParts[] = refId.split("_");
		String pathName = refIdParts[0] + "/" + refIdParts[0] + "_" + refIdParts[1] + ".xml";
		Map<String, Map<String, String>> dev = loadDeviceDescription(zf, pathName);
		Map<String, String> cobjref = dev.get(refId);
		if (cobjref == null)
			throw new IllegalArgumentException("Unable to find ComObjectRef with Id " + refId + " in " + pathName);
		// Perhaps the ComObjectRef
		if (processETS4ComObj(cobjref, zf, address, name, useObjectSize))
			return true;

		String refco = cobjref.get("RefId");
		Map<String, String> cobj = dev.get(refco);
		if (cobj == null)
			throw new IllegalArgumentException("Unable to find ComObject with Id " + refco + " in " + pathName);

		if (processETS4ComObj(cobj, zf, address, name, useObjectSize))
			return true;

		return false;
	}

	private boolean processETS4ComObj(Map<String, String> cobj, ZipFile zf, String address, String name,
			boolean useObjectSize) throws SAXException, IOException, ParserConfigurationException, KNXException {
		String dpt = cobj.get("DatapointType");
		if (dpt != null && dpt.length() != 0) {
			storeGAInfo(address, name, dpt);
			return true;
		}
		if (useObjectSize) {
			String objSize = cobj.get("ObjectSize");
			if (objSize != null && objSize.length() != 0) {
				// "1 Bit" is pretty unambigious -- no warning for that
				if (!"1 Bit".equals(objSize))
					L.warning("Warning: Infering DPT for " + new GroupAddress(Integer.parseInt(address)) + " (" + name
							+ ") by objSize " + objSize
							+ " - this is not good, please update your ETS4/ETS project with proper DPT specifications!");
				storeGAInfo(address, name, inferDPTFromObjectSize(zf, objSize));
				return true;
			}
		}
		return false;
	}

	private String inferDPTFromObjectSize(ZipFile zf, String objSize)
			throws SAXException, IOException, ParserConfigurationException {
		// Take a guess based on size
		String dpitid = null;
		// Some standard things
		if ("1 Bit".equals(objSize))
			dpitid = "1-1";
		else if ("1 Byte".equals(objSize))
			dpitid = "5-1";
		else if ("2 Bytes".equals(objSize))
			dpitid = "9-1";
		else {
			if (dptMap == null) {
				dptMap = new HashMap<>();
				SAXParser saxParser = saxFactory.newSAXParser();
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
				saxParser.parse(zf.getInputStream(new ZipEntry("knx_master.xml")), gaHandler);
			}
			String sizeSpec[] = objSize.split(" ");
			int bits = Integer.parseInt(sizeSpec[0]);
			if (sizeSpec[1].startsWith("Byte"))
				bits *= 8;
			return dptMap.get(bits);
		}
		return "DPST-" + dpitid;
	}

	private Map<String, Map<String, String>> loadDeviceDescription(ZipFile zf, String filename)
			throws ParserConfigurationException, SAXException, IOException {
		if (deviceDescriptionCache == null) {
			saxFactory = SAXParserFactory.newInstance();
			deviceDescriptionCache = new HashMap<>();
		} else {
			Map<String, Map<String, String>> cacheEntry = deviceDescriptionCache.get(filename);
			if (cacheEntry != null) {
				return cacheEntry;
			}
		}
		ZipEntry ze = zf.getEntry(filename);
		if (ze == null)
			throw new IllegalArgumentException("Unable to find device description " + filename);
		final Map<String, Map<String, String>> attrById = new HashMap<>();
		SAXParser saxParser = saxFactory.newSAXParser();
		DefaultHandler gaHandler = new DefaultHandler() {
			@Override
			public void startElement(String uri, String localName, String qName, Attributes attr) throws SAXException {
				if ("ComObjectRef".equals(qName) || "ComObject".equals(qName)) {
					// Convert the mutable Attributes object
					Map<String, String> pattr = new HashMap<>();
					for (int ix = 0; ix < attr.getLength(); ix++)
						pattr.put(attr.getQName(ix), attr.getValue(ix));
					attrById.put(pattr.get("Id"), pattr);
				}
			}
		};
		saxParser.parse(zf.getInputStream(ze), gaHandler);
		deviceDescriptionCache.put(filename, attrById);
		return attrById;
	}

}
