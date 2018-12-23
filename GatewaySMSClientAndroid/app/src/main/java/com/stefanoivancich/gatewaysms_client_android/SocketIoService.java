package com.stefanoivancich.gatewaysms_client_android;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.provider.Settings;
import android.telephony.SmsManager;
import android.util.Log;

// Socket IO libraries
import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;
import org.json.JSONException;
import org.json.JSONObject;
import java.net.URISyntaxException;

// EventBus library
import org.greenrobot.eventbus.Subscribe;


public class SocketIoService extends Service {

  private static boolean isRunning = false; // Is the Service Active?
  private static boolean isConnected = false; // Is the Service connected to the server?

  private String deviceId;
  private String uri;
  private int messagesSent=0;
  private int messagesNOTSent=0;
  private Socket socket;

  // Is the Service Active?
  public static boolean isRunning() {
    return isRunning;
  }
  // Is the Service connected to the server?
  public static boolean icConnected(){
    return isConnected;
  }

  public SocketIoService() {
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    isRunning = true;

    Log.d("SocketIOService","ON START COMMAND");

    // Register the event to subscribe.
    GlobalBus.getBus().register(this);

    deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

    uri="http://192.168.1.105:3000";
    connectToSocket();

    return START_STICKY;
  }

  @Override
  public void onDestroy() {
    isRunning = false;

    Log.d("SocketIOService","ON DESTROY");

    // Unregister the registered event.
    GlobalBus.getBus().unregister(this);
  }



  private void connectToSocket(){
    Log.d("SocketIOService","ConnectToSocket method");
    //Connect Socket client to the server
    try{
      // Connect to socket
      IO.Options opts = new IO.Options(); //Needed otherwise every time creates multiple connections
      opts.forceNew=true;
      opts.reconnection=false;
      socket = IO.socket(uri,opts);
      socket.connect();

      // Listen the Connection Acknowledge
      socket.on("conACK", new Emitter.Listener() {
        @Override
        public void call(Object... args) {
          Log.d("SocketIOService","Connection Acknowledge");

          // Send to server my Android unique device id
          socket.emit("idAndroid",deviceId);

          // Set UI components to ACTIVE state
          Events.activeUI event = new Events.activeUI(true);
          GlobalBus.getBus().post(event);

        }
      });


      // Listen the message to be sent
      socket.on("message", new Emitter.Listener() {
        @Override
        public void call(Object... args) {
          JSONObject data = (JSONObject) args[0];
          try {
            // Extract data from socket
            String number = data.getString("number");
            String text = data.getString("text");
            String idSMS = data.getString("idSMS");

            Log.d("SocketIOService",number+" "+text+" "+idSMS);

            // Send SMS
            try{
              SmsManager smgr = SmsManager.getDefault();
              smgr.sendTextMessage(number,null,text,null,null);

              // Send to server SMS Acknowledge
              socket.emit("smsACK",idSMS);

              // Update UI
              messagesSent++;
              Events.updateHFParams event = new Events.updateHFParams(messagesSent,messagesNOTSent);
              GlobalBus.getBus().post(event);

              // Update LOG Fragment
              Events.HomeToLog homeToLogEvent =
                  new Events.HomeToLog("SENT: "+number+": "+text+"\n");
              GlobalBus.getBus().post(homeToLogEvent);

            }catch (Exception e){
              // Send to server sms NOT Acknowledge
              socket.emit("smsNACK",idSMS);

              // Update UI
              messagesNOTSent++;
              Events.updateHFParams event = new Events.updateHFParams(messagesSent,messagesNOTSent);
              GlobalBus.getBus().post(event);

              // Update LOG Fragment
              Events.HomeToLog homeToLogEvent =
                  new Events.HomeToLog("NOT SENT: "+number+": "+text+"\n");
              GlobalBus.getBus().post(homeToLogEvent);

            }


          }catch (JSONException e) {
            e.printStackTrace();
          }


        }
      });

    }catch (URISyntaxException e) {
      e.printStackTrace();
    }

  }

  private void disconnectFromSocket(){
    socket.disconnect();
  }


////// EVENT BUS EVENTS /////////////////////////////////////////////////////
// EventBus is a library used to communicate with others app components

  @Subscribe
  public void connectEvent(Events.connect event){
    Log.d("SocketIOService","ConnectEvent method");

    uri = event.getUri();
    connectToSocket();
  }

  @Subscribe
  public void disconnectEvent(Events.disconnect event){
    Log.d("SocketIOService","DisconnectEvent method");
    disconnectFromSocket();
  }









  @Override
  public IBinder onBind(Intent intent) {
    // TODO: Return the communication channel to the service.
    throw new UnsupportedOperationException("Not yet implemented");
  }
}