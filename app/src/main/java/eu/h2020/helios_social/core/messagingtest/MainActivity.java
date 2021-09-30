package eu.h2020.helios_social.core.messagingtest;

import android.content.Context;
import android.os.Bundle;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;

import eu.h2020.helios_social.core.messaging.HeliosConnectionInfo;
import eu.h2020.helios_social.core.messaging.HeliosIdentityInfo;
import eu.h2020.helios_social.core.messaging.HeliosMessage;
import eu.h2020.helios_social.core.messaging.HeliosMessageListener;
import eu.h2020.helios_social.core.messaging.HeliosMessagingException;
import eu.h2020.helios_social.core.messaging.HeliosTopic;
import eu.h2020.helios_social.core.messaging.ReliableHeliosMessagingNodejsLibp2pImpl;
import eu.h2020.helios_social.core.messaging.data.HeliosMessagePart;
import eu.h2020.helios_social.core.messaging.data.JsonMessageConverter;
import eu.h2020.helios_social.core.messaging.sync.HeartbeatManager;

public class MainActivity extends AppCompatActivity implements HeliosMessageListener {
    private final HeliosMessageListener listener = this;
    private boolean subscribed = false;
    private HeliosTopic topic;
    private String mUserUUID = "cfa1ec74-555b-4e24-8dff-9d489d76fb2e";
    private ReliableHeliosMessagingNodejsLibp2pImpl transport = null;
    private HeliosTopic testTopic = null;
    private int testseq = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        TextView textView = (TextView)findViewById(R.id.txt_area);
        textView.setMovementMethod(new ScrollingMovementMethod());
        textView.append(VersionUtils.getAndroidVersion() + "\n" + VersionUtils.getDeviceName() + "\n");

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                boolean ok;
                String now = java.text.DateFormat.getDateTimeInstance().format(Calendar.getInstance().getTime());
                textView.append("\n");
                switch (testseq) {
                    case 0:
                        textView.append("Init " + now + "\n");
                        ok = init();
                        if (ok) {
                            textView.append("[Ok]\n");
                        } else {
                            textView.append("[Fail]\n");
                        }
                        break;
                    case 1:
                        textView.append("Connect " + now + "\n");
                        ok = connect();
                        if (ok) {
                            textView.append("[Ok]\n");
                        } else {
                            textView.append("[Fail]\n");
                        }
                        break;
                    case 2:
                        textView.append("Subscribe " + now + "\n");
                        ok = subscribe();
                        if (ok) {
                            textView.append("[Ok]\n");
                        } else {
                            textView.append("[Fail]\n");
                        }
                        break;
                    case 3:
                        textView.append("Publish " + now + "\n");
                        ok = publish("Hi " + now);
                        if (ok) {
                            textView.append("[Ok]\n");
                        } else {
                            textView.append("[Fail]\n");
                        }
                        break;
                    case 4:
                        textView.append("Unsubscribe " + now + "\n");
                        ok = unsubscribe();
                        if (ok) {
                            textView.append("[Ok]\n");
                        } else {
                            textView.append("[Fail]\n");
                        }
                        break;
                    case 5:
                        textView.append("Disconnect " + now + "\n");
                        ok = disconnect();
                        if (ok) {
                            textView.append("[Ok]\n");
                        } else {
                            textView.append("[Fail]\n");
                        }
                        break;
                    case 6:
                        textView.append("Stop " + now + "\n");
                        ok = stop();
                        if (ok) {
                            textView.append("[Ok]\n");
                        } else {
                            textView.append("[Fail]\n");
                        }
                        break;
                    default:
                        textView.append("Test sequence run " + now + "\n");
                        break;
                }
                testseq++;
            }
        });
    }

    private ReliableHeliosMessagingNodejsLibp2pImpl getTransport(Context ctx) {
        ReliableHeliosMessagingNodejsLibp2pImpl s = ReliableHeliosMessagingNodejsLibp2pImpl.getInstance();
        s.setContext(ctx);
        return s;
    }

    private boolean init() {
        if (transport == null) {
            transport = getTransport(this);
        }
        return (transport != null);
    }

    private boolean subscribe() {
        if (transport == null) {
            transport = getTransport(this);
        }
        testTopic = new HeliosTopic("test", "");
        try {
            transport.subscribe(testTopic, this);
        } catch (HeliosMessagingException e) {
            return false;
        }
        return true;
    }

    private boolean publish(String msg) {
        if (transport == null) {
            transport = getTransport(this);
        }
        try {
            String ts = DateTimeFormatter.ISO_ZONED_DATE_TIME.format(ZonedDateTime.now());
            HeliosMessagePart msgPart = new HeliosMessagePart(msg, "nick", mUserUUID, "test", ts);
            HeliosMessage jsonMsg = new HeliosMessage(JsonMessageConverter.getInstance().convertToJson(msgPart));
            transport.publish(testTopic, jsonMsg);
        } catch (HeliosMessagingException e) {
            return false;
        }
        return true;
    }

    private boolean unsubscribe() {
        if (transport == null) {
            transport = getTransport(this);
        }
        try {
            transport.unsubscribe(testTopic);
            testTopic = null;
        } catch (HeliosMessagingException e) {
            return false;
        }
        return true;
    }

    private boolean connect() {
        if (transport == null) {
            transport = getTransport(this);
        }
        try {
            transport.connect(new HeliosConnectionInfo(), new HeliosIdentityInfo("nick", mUserUUID));
        } catch (HeliosMessagingException e) {
            return false;
        }
        return true;
    }

    private boolean disconnect() {
        if (transport == null) {
            transport = getTransport(this);
        }
        try {
            transport.disconnect(new HeliosConnectionInfo(), new HeliosIdentityInfo("nick", mUserUUID));
        } catch (HeliosMessagingException e) {
            return false;
        }
        return true;
    }

    private boolean stop() {
        if (transport == null) {
            transport = getTransport(this);
        }
        try {
            transport.unsubscribeListener(this);
            transport.stop();
            HeartbeatManager.getInstance().stop();
            boolean disconnected = !transport.isConnected();
            return disconnected;
        } catch (HeliosMessagingException e) {
            return false;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        switch (id) {

            case R.id.action_exit:
                finishAffinity();
                System.exit(0);
                return true;

            default:
                // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.
                return false;
        }
    }

    @Override
    public void showMessage(HeliosTopic topic, HeliosMessage message) {

        Log.e("HELIOS", "Activity: topic:" + topic.getTopicName() + ", message: " + message.getMessage());
    }
}
