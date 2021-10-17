package com.tellerulam.knx2mqtt;

import java.nio.charset.StandardCharsets;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import com.eclipsesource.json.JsonObject;

public class MQTTHandler {
	private final Logger L = Logger.getLogger(getClass().getName());

	public static MQTTHandler create() throws MqttException {
		MQTTHandler result = new MQTTHandler();
		result.doInit();
		return result;
	}

	private final String topicPrefix;

	private MQTTHandler() {
		String tp = System.getProperty(PropertyNames.KNX2MQTT_MQTT_TOPIC, "knx");
		if (!tp.endsWith("/"))
			tp += "/";
		topicPrefix = tp;
	}

	private MqttClient mqttc;

	private void queueConnect() {
		shouldBeConnected = false;
		Main.t.schedule(new TimerTask() {
			@Override
			public void run() {
				doConnect();
			}
		}, 10 * 1000);
	}

	private class StateChecker extends TimerTask {
		@Override
		public void run() {
			if (!mqttc.isConnected() && shouldBeConnected) {
				L.warning("Should be connected but aren't, reconnecting");
				queueConnect();
			}
		}
	}

	private boolean shouldBeConnected;

	private boolean _connected;

	private void processSetGet(String namePart, MqttMessage msg, boolean set) {
		if (msg.isRetained()) {
			L.finer("Ignoring retained message " + msg + " to " + namePart);
			return;
		}
		// Now translate the topic into a group address
		GroupAddressInfo gai = GroupAddressManager.getGAInfoForName(namePart);
		if (gai == null) {
			L.warning("Unable to translate name " + namePart + " into a group address, ignoring message " + msg);
			return;
		}
		String address = gai.getAddress();
		L.fine("Name " + namePart + " translates to GA " + address);
		String data = new String(msg.getPayload(), StandardCharsets.UTF_8);
		if (set)
			KNXConnector.doGroupWrite(address, data, gai);
		else
			KNXConnector.doGroupRead(address, data, gai);
	}

	void processMessage(String topic, MqttMessage msg) {
		L.fine("Received " + msg + " to " + topic);
		topic = topic.substring(topicPrefix.length(), topic.length());
		if (topic.startsWith("set/"))
			processSetGet(topic.substring(4), msg, true);
		else if (topic.startsWith("get/"))
			processSetGet(topic.substring(4), msg, false);
		else
			L.warning("Ignored message " + msg + " to unknown topic " + topic);
	}

	private void doConnect() {
		L.info("Connecting to MQTT broker " + mqttc.getServerURI() + " with CLIENTID=" + mqttc.getClientId()
				+ " and TOPIC PREFIX=" + topicPrefix);

		MqttConnectOptions copts = new MqttConnectOptions();
		copts.setWill(topicPrefix + "connected", "0".getBytes(), 1, true);
		copts.setCleanSession(true);
		try {
			mqttc.connect(copts);
			sendConnectionState();
			L.info("Successfully connected to broker, subscribing to " + topicPrefix + "(set|get)/#");
			try {
				mqttc.subscribe(topicPrefix + "set/#", 1);
				mqttc.subscribe(topicPrefix + "get/#", 1);
				shouldBeConnected = true;
			} catch (MqttException mqe) {
				L.log(Level.WARNING, "Error subscribing to topic hierarchy, check your configuration", mqe);
				throw mqe;
			}
		} catch (MqttException mqe) {
			L.log(Level.WARNING, "Error while connecting to MQTT broker, will retry: " + mqe.getMessage(), mqe);
			queueConnect(); // Attempt reconnect
		}
	}

	private void doInit() throws MqttException {
		String server = System.getProperty(PropertyNames.KNX2MQTT_MQTT_SERVER, "tcp://localhost:1883");
		String clientID = System.getProperty(PropertyNames.KNX2MQTT_MQTT_CLIENTID, "knx2mqtt");
		mqttc = new MqttClient(server, clientID, new MemoryPersistence());
		mqttc.setCallback(new MqttCallback() {
			@Override
			public void messageArrived(String topic, MqttMessage msg) throws Exception {
				try {
					processMessage(topic, msg);
				} catch (Exception e) {
					L.log(Level.WARNING, "Error when processing message " + msg + " for " + topic, e);
				}
			}

			@Override
			public void deliveryComplete(IMqttDeliveryToken token) {
				/* Intentionally ignored */
			}

			@Override
			public void connectionLost(Throwable t) {
				L.log(Level.WARNING, "Connection to MQTT broker lost", t);
				queueConnect();
			}
		});
		doConnect();
		Main.t.schedule(new StateChecker(), 30 * 1000, 30 * 1000);
	}

	public void doPublish(String name, Object val, String src, String dpt, String textual, long updateTime,
			long lastChange) {
		JsonObject jso = new JsonObject();
		jso.add("ts", updateTime).add("lc", lastChange).add("knx_src_addr", src).add("knx_dpt", dpt);
		if (textual != null)
			jso.add("knx_textual", textual);
		if (val instanceof Integer)
			jso.add("val", ((Integer) val).intValue());
		else if (val instanceof Number)
			jso.add("val", ((Number) val).doubleValue());
		else
			jso.add("val", val.toString());
		String txtmsg = jso.toString();
		MqttMessage msg = new MqttMessage(jso.toString().getBytes(StandardCharsets.UTF_8));
		msg.setQos(0);
		msg.setRetained(true);
		try {
			String fullTopic = topicPrefix + "status/" + name;
			mqttc.publish(fullTopic, msg);
			L.finer("Published " + txtmsg + " to " + fullTopic);
		} catch (MqttException e) {
			L.log(Level.WARNING, "Error when publishing message " + txtmsg, e);
		}
	}

	private void sendConnectionState() {
		try {
			mqttc.publish(topicPrefix + "connected", (_connected ? "2" : "1").getBytes(), 1,
					true);
		} catch (MqttException e) {
			/* Ignore */
		}

	}

	public void doSetKNXConnectionState(boolean connected) {
		_connected = connected;
		sendConnectionState();
	}

}
