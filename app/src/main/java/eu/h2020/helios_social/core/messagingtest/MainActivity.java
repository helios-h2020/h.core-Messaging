package eu.h2020.helios_social.core.messagingtest;

import android.os.Bundle;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;

import eu.h2020.helios_social.core.messaging.HeliosConnectionInfo;
import eu.h2020.helios_social.core.messaging.HeliosIdentityInfo;
import eu.h2020.helios_social.core.messaging.HeliosMessage;
import eu.h2020.helios_social.core.messaging.HeliosMessageListener;
import eu.h2020.helios_social.core.messaging.HeliosMessagingException;
import eu.h2020.helios_social.core.messaging.HeliosTopic;

public class MainActivity extends AppCompatActivity implements HeliosMessageListener {
    private final HeliosMessageListener listener = this;
    private boolean subscribed = false;
    private HeliosTopic topic;
    private String mUserUUID = "cfa1ec74-555b-4e24-8dff-9d489d76fb2e";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
    }

    private void subscribe(){
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

            case R.id.action_sub:
                subscribe();
                return true;

            case R.id.action_unsub:
                return true;

            case R.id.action_connect:
                return true;

            case R.id.action_disconnect:
                return true;

            default:
                // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void showMessage(HeliosTopic topic, HeliosMessage message) {

        Log.e("HELIOS", "Activity: topic:" + topic.getTopicName() + ", message: " + message.getMessage());
    }
}
