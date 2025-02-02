package com.tellerulam.knx2mqtt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Timer;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.paho.client.mqttv3.MqttException;

public class Main {
	static final Logger L = Logger.getLogger(Main.class.getName());

	static final Timer t = new Timer(true);

	private static String getVersion() {
		// First, try the manifest tag
		String version = Main.class.getPackage().getImplementationVersion();
		if (version == null) {
			// Read from build.gradle instead
			try {
				List<String> buildFile = Files.readAllLines(Paths.get("build.gradle"), StandardCharsets.UTF_8);
				Pattern p = Pattern.compile("version.*=.*'([^']+)");
				for (String l : buildFile) {
					Matcher m = p.matcher(l);
					if (m.find())
						return m.group(1);
				}
			} catch (IOException e) {
				/* Ignore, no version */
			}
		}
		return version;
	}

	public static void main(String[] args) throws MqttException, SecurityException, IOException {
		// Interpret all command line arguments as property definitions (without the knx2mqtt
		// prefix)
		for (String s : args) {
			String sp[] = s.split("=", 2);
			if (sp.length != 2) {
				System.out.println("Invalid argument (no =): " + s);
				System.exit(1);
			}
			System.setProperty("knx2mqtt." + sp[0], sp[1]);
		}
		Logger.getLogger(Main.class.getName())
				.info("knx2mqtt V" + getVersion() + " (C) 2015 Oliver Wagner <owagner@tellerulam.com>");
		SyslogHandler.readConfig();
		GroupAddressManager addressManager = new GroupAddressManager();
		loadEtsProject(addressManager);
		loadAddressTable(addressManager);
		MQTTHandler mqtt = MQTTHandler.create(addressManager);
		KNXConnector.launch(addressManager, mqtt);
	}

	private static void loadAddressTable(GroupAddressManager addressManager) {
		String fileName = getAddressTableFileName();
		if (fileName == null) {
			L.config("No Group Address table specified");
		} else {
			new AddressTableLoader(addressManager).load(fileName);
		}
	}

	private static String getAddressTableFileName() {
		return System.getProperty(PropertyNames.KNX2MQTT_KNX_GROUPADDRESSTABLE);
	}

	private static void loadEtsProject(GroupAddressManager addressManager) {
		String fileName = getEtsProjectFileName();
		if (fileName == null) {
			L.config("No ETS4/ETS5 project file specified");
		} else {
			EtsLoader.load(addressManager, fileName);
		}
	}

	private static String getEtsProjectFileName() {
		String fileName = System.getProperty(PropertyNames.KNX2MQTT_KNX_ETS5PROJECTFILE);
		if (fileName == null) {
			fileName = System.getProperty(PropertyNames.KNX2MQTT_KNX_ETS4PROJECTFILE);
		}
		return fileName;
	}
}
