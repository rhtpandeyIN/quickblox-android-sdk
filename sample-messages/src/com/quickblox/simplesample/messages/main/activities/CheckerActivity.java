package com.quickblox.simplesample.messages.main.activities;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;

import com.qb.gson.JsonArray;
import com.qb.gson.JsonElement;
import com.qb.gson.JsonObject;
import com.qb.gson.JsonParser;
import com.quickblox.core.QBEntityCallbackImpl;
import com.quickblox.core.helper.StringifyArrayList;
import com.quickblox.messages.QBMessages;
import com.quickblox.messages.model.QBEnvironment;
import com.quickblox.messages.model.QBEvent;
import com.quickblox.messages.model.QBNotificationType;
import com.quickblox.messages.model.QBPushType;
import com.quickblox.simplesample.messages.R;
import com.quickblox.simplesample.messages.main.Consts;
import com.quickblox.simplesample.messages.main.ReportAdapter;
import com.quickblox.simplesample.messages.main.helper.PlayServicesHelper;
import com.quickblox.simplesample.messages.main.models.Credentials;
import com.quickblox.simplesample.messages.main.models.Report;
import com.quickblox.simplesample.messages.main.utils.DialogUtils;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

/**
 * Created by tereha on 15.09.15.
 */
public class CheckerActivity extends Activity {
    private String TAG = CheckerActivity.class.getSimpleName();
    private Button startCheckerBtn;
    private ListView messagesList;
    private PlayServicesHelper playServicesHelper;
    private ArrayList<Report> listReports = new ArrayList<>();
    private ReportAdapter reportAdapter;
    private ProgressBar checkerPB;
    private Thread t;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.push_notification_checker_layout);
        initUI();

        playServicesHelper = new PlayServicesHelper(this);

        initUI();
        // Register to receive push notifications events
        //
        LocalBroadcastManager.getInstance(this).registerReceiver(mPushReceiver,
                new IntentFilter(Consts.NEW_PUSH_EVENT));

    }

    private void initUI() {
        startCheckerBtn = (Button) findViewById(R.id.startCheckerBtn);
        messagesList = (ListView) findViewById(R.id.messagesList);
        checkerPB = (ProgressBar) findViewById(R.id.startCheckerPB);
        reportAdapter = new ReportAdapter(this, listReports);
        messagesList.setAdapter(reportAdapter);
    }


    public void startChecker(View view) {
        checkerPB.setVisibility(View.VISIBLE);


        final Handler handler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                Bundle bundle = msg.getData();
                QBEvent qbEvent = (QBEvent) bundle.getSerializable(Consts.QBEVENT_EXTRAS);
                sendPushNotification(qbEvent);
            }

        };

        t = new Thread(new Runnable() {
            public void run() {
                loadServersData();
//                try {
//                    do {
//                        Message msg = handler.obtainMessage();
//                        Bundle bundle = new Bundle();
//                        bundle.putSerializable(Consts.QBEVENT_EXTRAS, createPushNotificationEvent());
//                        msg.setData(bundle);
//                        handler.sendMessage(msg);
//                        TimeUnit.MILLISECONDS.sleep(Consts.PUSH_TIMEOUT);
//                    } while (Consts.PUSH_TIMEOUT > 0);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
            }
        });
        t.start();
    }

    public void loadServersData() {
        ArrayList <Credentials> credentialsList = new ArrayList<>();

        HttpClient client = new DefaultHttpClient();
        HttpGet request = new HttpGet(Consts.INSTANCES_WEB_RESOURCE);

        try {
            HttpResponse response = client.execute(request);
            HttpEntity entity = response.getEntity();

            String content = EntityUtils.toString(entity);

            JsonParser parser = new JsonParser();
            JsonArray mainObject = parser.parse(content).getAsJsonArray();

            for (JsonElement instance : mainObject) {
                JsonObject instanceObject = instance.getAsJsonObject();

                Credentials credentials = new Credentials();
                credentials.setTitle(instanceObject.get(Consts.INSTANCES_TITLE).getAsString());
                credentials.setAppId(instanceObject.get(Consts.INSTANCES_APP_ID).getAsString());
                credentials.setAuthKey(instanceObject.get(Consts.INSTANCES_AUTH_KEY).getAsString());
                credentials.setAuthSecret(instanceObject.get(Consts.INSTANCES_AUTH_SECRET).getAsString());
                credentials.setUserLogin(instanceObject.get(Consts.INSTANCES_USER_LOGIN).getAsString());
                credentials.setUserID(instanceObject.get(Consts.INSTANCES_USER_ID).getAsString());
                credentials.setUserPass(instanceObject.get(Consts.INSTANCES_USER_PASSWORD).getAsString());
                credentials.setServerApiDomain(instanceObject.get(Consts.INSTANCES_SERVER_API_DOMAIN).getAsString());

                credentialsList.add(credentials);
            }

            Log.d(TAG, "credentialsList.size() = " + credentialsList.size());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private QBEvent createPushNotificationEvent(){
        //Create QuickBlox Push Notification Event
        QBEvent qbEvent = new QBEvent();
        qbEvent.setNotificationType(QBNotificationType.PUSH);
        qbEvent.setEnvironment(QBEnvironment.DEVELOPMENT);
        qbEvent.setPushType(QBPushType.GCM);

        // generic push - will be delivered to all platforms (Android, iOS, WP, Blackberry..)
        long currentTimeMillis = System.currentTimeMillis();

        HashMap<String, String> data = new HashMap<>();
        data.put("data." + Consts.EXTRA_MESSAGE, "");
        data.put("data." + Consts.EXTRA_PUSH_ID, "id");
        data.put("data." + Consts.EXTRA_DATE, String.valueOf(currentTimeMillis));
        data.put("data." + Consts.EXTRA_SERVER_TITLE, "title");

        qbEvent.setMessage(data);

        StringifyArrayList<Integer> userIds = new StringifyArrayList<>();
        userIds.add(2224038);
        qbEvent.setUserIds(userIds);

        return qbEvent;
    }

    private void sendPushNotification(QBEvent qbEvent){
        QBMessages.createEvent(qbEvent, new QBEntityCallbackImpl<QBEvent>() {
            @Override
            public void onSuccess(QBEvent qbEvent, Bundle bundle) {
                Log.d(TAG, "pushSended");

            }

            @Override
            public void onError(List<String> strings) {
                Log.d(TAG, "pushErrorSend");
                // errors
                DialogUtils.showLong(CheckerActivity.this, strings.toString());

            }
        });
    }



    private BroadcastReceiver mPushReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "new broadcast message ");
            String message = intent.getStringExtra(Consts.EXTRA_MESSAGE);
            String date = intent.getStringExtra("date");
            String serverTitle = intent.getStringExtra("serverTitle");
//            Log.d(TAG, "message: " + message + "\ndata.date: " + date + "\ndata.serverTitle: " + serverTitle + "\n");

            for(String key : intent.getExtras().keySet()){
                Log.d(TAG, key + ": " /*+ intent.getExtras().getString(key)*/);
            }
//            Log.d(TAG, intent.getExtras().keySet());

            if (message != null) {
//                processingMessage(message);
            }
        }

    };

    private void processingMessage(String message){
        long timeFromMessage = Long.parseLong(message);
        long currentTimeMillis = System.currentTimeMillis();
        Date dateSend = new Date (timeFromMessage);
        Date currentDate = new Date (currentTimeMillis);
//                "yyyy-MM-dd",
//                "yyyy-MM-dd HH:mm",
//                "yyyy-MM-dd HH:mmZ",
//                "yyyy-MM-dd HH:mm:ss.SSSZ",
//                "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
        SimpleDateFormat df2 = new SimpleDateFormat("HH:mm:ss.SSS");
        String dateSendText = df2.format(dateSend);
        String currentDateText = df2.format(currentDate);
        String travelingDateText = String.valueOf((currentTimeMillis - timeFromMessage)/1000);
        Log.d(TAG, "\n"
                + "\ndateSend = " + dateSendText
                + "\n" + "currentDateText = " + currentDateText
                + "\n" + "timeout = " + (currentTimeMillis - timeFromMessage) / 1000 + " sec");

        Report report = new Report (dateSendText, currentDateText, travelingDateText);
        listReports.add(report);
        reportAdapter.notifyDataSetChanged();
    }


    public void stopChecker(View view) {
        if (t != null) {
            Thread dummy = t;
            t = null;
            dummy.interrupt();
        }

        checkerPB.setVisibility(View.GONE);
    }

    @Override
    protected void onDestroy() {
        // Unregister since the activity is about to be closed.
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mPushReceiver);

        super.onDestroy();
    }
}
