package adurax.SosiDboxSMS;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.client2.exception.DropboxException;
import com.dropbox.client2.session.AppKeyPair;

public class MainActivity extends Activity {
	private static final String TAG = "MainActivity";

	private static final String APP_KEY = "8ie5okkuqwwcz3k";//please add your app key here
	private static final String APP_SECRET = "utgqid6oa4n1j91";//please add your app secret here
	// You don't need to change these, leave them alone.
	private static final String ACCOUNT_PREFS_NAME = "prefs";
	private static final String ACCESS_KEY_NAME = "ACCESS_KEY";
	private static final String ACCESS_SECRET_NAME = "ACCESS_SECRET";
	DropboxAPI<AndroidAuthSession> mApi;
    DropboxAPI.Entry dirent = null;
    DropboxAPI.DropboxFileInfo info = null;
    private boolean mLoggedIn;

    String SosiDBMsg = "SosiDBMsg";
    String RootPath = Environment.getExternalStorageDirectory().toString(); // Rootpath of Android storage
    String DownloadToDir = RootPath + "/" + SosiDBMsg + "/";
    String DropBoxPath = "/AduraX/PhoneSMS/"; // "/AduraX/SosiDB/PhoneSMS/";
    String Axiv, AxivNo, AxivTitle, AxivFName, AxivMName, AxivLName, AxivPara1, AxivPara2;

    // Android widgets
    private Button btnLink;
    private Button btnAutoRun;
	private LinearLayout mDisplay;
    private Button btnSendSMS;
	private Button btnLoadText;
    private EditText txSMSMsg;
    final Handler handler = new Handler();

    int Sel = 0;
    boolean isPrint = false;
    boolean isFirstInterval = true;
    boolean isRun = true;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// We create a new AuthSession so that we can use the Dropbox API.
		AndroidAuthSession session = buildSession();
		mApi = new DropboxAPI<AndroidAuthSession>(session);

		// Basic Android widgets
		setContentView(R.layout.activity_main);
        mDisplay = (LinearLayout) findViewById(R.id.logged_in_display);
        txSMSMsg  =(EditText) findViewById(R.id.txSMSMessage);

        //region This button logs you out if you're logged in, or vice versa
        btnLink = (Button) findViewById(R.id.btnLink);
        btnLink.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                if (mLoggedIn) {
                    logOut();
                } else {
                    // Start the remote authentication
                    mApi.getSession().startOAuth2Authentication(MainActivity.this);
                }
            }
        });  //endregion

        //region This button runs or unruns schedule
        btnAutoRun = (Button) findViewById(R.id.btnAutoRun);
        btnAutoRun.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                if (isRun) {
                   handler.post(runnableCode);
                    btnAutoRun.setText("Unrun");
                    isRun = false;
                } else {
                   handler.removeCallbacks(runnableCode);
                    btnAutoRun.setText("Run");
                    isRun = true;
                    isFirstInterval = true;
                }
            }
		});  //endregion

        //region This is the button to send SMS
        btnSendSMS = (Button) findViewById(R.id.btnSendSMS);
        btnSendSMS.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new Download().execute();
            }
        });    //endregion

        //region This is the button to load text
        btnLoadText = (Button) findViewById(R.id.btnLoadText);
        btnLoadText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                File SosiDBAxivFile = new File(new File(Environment.getExternalStorageDirectory(), SosiDBMsg), "SosiDBAxivFile.txt");
                StringBuilder text = new StringBuilder(); //Read text from file
                try {
                    BufferedReader br = new BufferedReader(new FileReader(SosiDBAxivFile));
                    String line;
                    while ((line = br.readLine()) != null) {
                        text.append(line);   text.append('\n');
                    }
                    br.close();
                }
                catch (IOException e) { /*You'll need to add proper error handling here*/ }
                txSMSMsg.setText(text + "\n******* End Of File *******");
            }
        });
        //endregion

        //region for Network availability
        if (isNetworkAvailable()) {
            // Display the proper UI state if logged in or not
            setLoggedIn(mApi.getSession().isLinked());
        } else {
            txSMSMsg.setText("**************************\nWelcome to SosiDboxSMS. \n\n**************************\n\nInternet is not available! \n\n**************************\n\nThank you for using SosiDboxSMS.");
        }//endregion

	}

	@Override
	protected void onResume() {
        super.onResume();

        if (isNetworkAvailable()) {
            AndroidAuthSession session = mApi.getSession();

            // The next part must be inserted in the onResume() method of the
            // activity from which session.startAuthentication() was called, so
            // that Dropbox authentication completes properly.
            if (session.authenticationSuccessful()) {
                try {
                    // Mandatory call to complete the auth
                    session.finishAuthentication();

                    // Store it locally in our app for later use
                    storeAuth(session);
                    setLoggedIn(true);
                } catch (IllegalStateException e) {
                    showToast("Couldn't authenticate with Dropbox:" + e.getLocalizedMessage());
                    Log.i(TAG, "Error authenticating", e);
                }
            }
        } else {
            txSMSMsg.setText("**************************\nWelcome to SosiDboxSMS. \n\n**************************\n\nInternet is not available! \n\n**************************\n\nThank you for using SosiDboxSMS.");
        }
    }

    public boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        return connectivityManager.getActiveNetworkInfo() != null && connectivityManager.getActiveNetworkInfo().isConnected();
    }

	private void logOut() {
		// Remove credentials from the session
		mApi.getSession().unlink();

		// Clear our stored keys
		clearKeys();
		// Change UI state to display logged out version
		setLoggedIn(false);
	}

	/**
	 * Convenience function to change UI state based on being logged in
	 */
	private void setLoggedIn(boolean loggedIn) {
		mLoggedIn = loggedIn;
		if (loggedIn) {
            btnLink.setText("Unlink from Dropbox");
			mDisplay.setVisibility(View.VISIBLE);

            if(isExternalStorageWritable() == true) {}
            else {
                Toast.makeText(getApplicationContext(), "External storage not accessible. ", Toast.LENGTH_LONG).show();
                return;
            }
		} else {
			btnLink.setText("Link with Dropbox");
			mDisplay.setVisibility(View.GONE);
		}
	}

	private void showToast(String msg) {
		Toast error = Toast.makeText(this, msg, Toast.LENGTH_LONG);
		error.show();
	}

	/**
	 * Shows keeping the access keys returned from Trusted Authenticator in a
	 * local store, rather than storing user name & password, and
	 * re-authenticating each time (which is not to be done, ever).
	 */
	private void loadAuth(AndroidAuthSession session) {
		SharedPreferences prefs = getSharedPreferences(ACCOUNT_PREFS_NAME, 0);
		String key = prefs.getString(ACCESS_KEY_NAME, null);
		String secret = prefs.getString(ACCESS_SECRET_NAME, null);
		if (key == null || secret == null || key.length() == 0 || secret.length() == 0) return;

		if (key.equals("oauth2:")) {// If the key is set to "oauth2:", then we can assume the token is for OAuth 2.
			session.setOAuth2AccessToken(secret);
		}
	}

	/**
	 * Shows keeping the access keys returned from Trusted Authenticator in a
	 * local store, rather than storing user name & password, and
	 * re-authenticating each time (which is not to be done, ever).
	 */
	private void storeAuth(AndroidAuthSession session) {
		// Store the OAuth 2 access token, if there is one.
		String oauth2AccessToken = session.getOAuth2AccessToken();
		if (oauth2AccessToken != null) {
			SharedPreferences prefs = getSharedPreferences(ACCOUNT_PREFS_NAME,
					0);
			Editor edit = prefs.edit();
			edit.putString(ACCESS_KEY_NAME, "oauth2:");
			edit.putString(ACCESS_SECRET_NAME, oauth2AccessToken);
			edit.commit();
			return;
		}
	}

	private void clearKeys() {
		SharedPreferences prefs = getSharedPreferences(ACCOUNT_PREFS_NAME, 0);
		Editor edit = prefs.edit();
		edit.clear();
		edit.commit();
	}

	private AndroidAuthSession buildSession() {
		AppKeyPair appKeyPair = new AppKeyPair(APP_KEY, APP_SECRET);

		AndroidAuthSession session = new AndroidAuthSession(appKeyPair);
		loadAuth(session);
		return session;
	}

    // Asynchronous method to download any file to dropbox
    public class Download extends AsyncTask<String, Void, String> {

        protected void onPreExecute(){}
        DropboxAPI.DropboxFileInfo info = null;
        String FileNo = null;

        protected String doInBackground(String... arg0) {
            try {
                dirent = mApi.metadata(DropBoxPath, 20, null, true, null);
            } catch (DropboxException e) {
                System.out.println("Error :  " + e.getMessage());
            }
            if(dirent.contents.isEmpty() == false) {
                for (DropboxAPI.Entry entry : dirent.contents) {
                    if (!entry.isDir) FileNo = entry.fileName();
                    try {
                        // Define path of file to be download
                        File file = new File(DownloadToDir + FileNo);
                        FileOutputStream outputStream = new FileOutputStream(file);
                        info = mApi.getFile(DropBoxPath + FileNo, null, outputStream, null);
                        Log.i("DbExampleLog", "The file's rev is: " + info.getMetadata().rev);
                        mApi.delete(DropBoxPath + FileNo);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                return info.getMetadata().rev;
            }
            else return "No file.";
        }

        @Override
        protected void onPostExecute(String result) {
            if(result.equals("No file.")){
                txSMSMsg.setText("**************************\nWelcome to SosiDboxSMS. \n\n**************************\n\nThere are no file in the Dropbox folder.\n\n**************************\n\nThank you for using SosiDboxSMS.");
                Toast.makeText(getApplicationContext(), "No file.", Toast.LENGTH_LONG).show();
                Log.e("DbExampleLog", result);
            }
            else {
                Toast.makeText(getApplicationContext(), "File downloaded ", Toast.LENGTH_LONG).show();
                Log.e("DbExampleLog", "The downloaded file's rev is: " + result);
            }
            //region  Load files in directory into files array
            String path = Environment.getExternalStorageDirectory().toString() + "/" + SosiDBMsg + "/";
            dir = new File(path);
            fileNames = dir.listFiles( //File fileNames[] = dir.listFiles
                    new FilenameFilter() {
                        public boolean accept(File dirx, String name) {
                            return name.toLowerCase().endsWith(".ssdb");
                        }
                    }
            );

            if (fileNames.length > 0)
            {
                btnSendSMS.setEnabled(false);
                SentDetails = "\n****** Sending SMS(s) from " + fileNames.length + " files at " + getCurrentTimeStamp() + " ****\n";
                Sel = 0;
                MsgFileName = fileNames[Sel];
                OpenFileFromDownlodDir(dir, MsgFileName);
                Sel++;
                txSMSMsg.setText(SentDetails);
            }else Toast.makeText(getApplicationContext(), "No SMS file for sending!", Toast.LENGTH_LONG).show();
            //endregion
        }
    }

    // Define the code block to be executed
    private Runnable runnableCode = new Runnable() {
        @Override
        public void run() {
            new Download().execute();
            handler.postDelayed(runnableCode, GetInterval());
        }
    };

    private long GetInterval() {
        if (!isFirstInterval) {
            return 8 * 60 * 60 * 1000;
        } else {
            Date now = new Date();
            SimpleDateFormat sdfDateInt = new SimpleDateFormat("HHmm");
            SimpleDateFormat sdfDateHour = new SimpleDateFormat("HH");
            SimpleDateFormat sdfDateMin = new SimpleDateFormat("mm");
            int nowTimeInt = Integer.parseInt(sdfDateInt.format(now));
            int nowMin = Integer.parseInt(sdfDateHour.format(now)) * 60 + Integer.parseInt(sdfDateMin.format(now));
            long Interval = 0;
            if (nowTimeInt < 0430)      Interval = (04 * 60 + 30 - nowMin) * 60 * 1000;
            else if (nowTimeInt < 1230) Interval = (14 * 60 + 45 - nowMin) * 60 * 1000;
            else if (nowTimeInt < 2030) Interval = (20 * 60 + 30 - nowMin) * 60 * 1000;
            else                        Interval = (04 * 60 + 30 - nowMin  + 24*60) * 60 * 1000;
            isFirstInterval = false;
            return Interval;
        }
    }

    //region *****************   Start personal coding here & constants & variables  **************************************
    private int mMessageSentParts, mMessageSentTotalParts, MsgSentCount, SentCount = 0;
    private String SENT, DELIVERED, message, Para1x, Para2x;
    private String splitNumber[], splitTitle[], splitFName[], splitLName[], splitPara1[], splitPara2[];
    private boolean Diff;
    private String SentDetails = "";
    private File fileNames[], dir, MsgFileName;//File fileNames[]
    //endregion

    private void OpenFileFromDownlodDir(File dir, File fileName)  {

        try {
            File file = new File(dir, fileName.getName());
            FileReader fileReader = new FileReader(file);   // FileReader reads text files in the default encoding.
            BufferedReader bufferedReader =  new BufferedReader(fileReader);  // Always wrap FileReader in BufferedReader.

            String SMSType = bufferedReader.readLine();
            if (SMSType.equals("DiffSms=No")) {
                String phoneNumbers = bufferedReader.readLine();
                String Msg = bufferedReader.readLine();
                StartSendMsgSt(phoneNumbers, Msg);
            }
            else {
                String phoneNumbers = bufferedReader.readLine();
                String Titlex = bufferedReader.readLine();
                String FNamex = bufferedReader.readLine();
                String LNamex = bufferedReader.readLine();
                String Para1 = bufferedReader.readLine();
                String Para2 = bufferedReader.readLine();
                String Msg = bufferedReader.readLine();
                StartSendMsgDt(phoneNumbers, Titlex, FNamex, LNamex, Para1, Para2, Msg);
            }
            bufferedReader.close();   // Always close files.  //fileReader.close();
        } catch (FileNotFoundException ex) {
            Toast.makeText(getBaseContext(), "Unable to open file '" + fileName + "'", Toast.LENGTH_SHORT).show();
        } catch (IOException ex) {
            Toast.makeText(getBaseContext(), "Error reading file '" + fileName + "'", Toast.LENGTH_SHORT).show();
        }
    }

    private void StartSendMsgDt(String phoneNumbers, String Titlex, String FNamex,
                                String LNamex, String Para1, String Para2, String Msg) {
        SENT = "SMS_SENT";
        DELIVERED = "SMS_DELIVERED";
        MsgSentCount = 0;
        registerBroadCastReceivers();
        message = Msg;

        Diff = true;
        Para1x = Para1; Para2x = Para2;
        splitNumber = phoneNumbers.split("; *");
        splitTitle = Titlex.split("; *");
        splitFName = FNamex.split("; *");
        splitLName = LNamex.split("; *");
        splitPara1 = Para1.split("; *");
        splitPara2 = Para2.split("; *");

        if (splitTitle[MsgSentCount] != "") Msg = Msg.replace("#Title", splitTitle[MsgSentCount]);
        if (splitFName[MsgSentCount] != "") Msg = Msg.replace("#FName", splitFName[MsgSentCount]);
        if (splitLName[MsgSentCount] != "") Msg = Msg.replace("#LName", splitLName[MsgSentCount]);
        if (Para1 != "") Msg = Msg.replace("#Para1", splitPara1[MsgSentCount]);
        if (Para2 != "") Msg = Msg.replace("#Para2", splitPara2[MsgSentCount]);

        SentDetails = SentDetails + "\nFile: " + MsgFileName.getName() + " with " +  splitNumber.length + " SMS(s)";
        Msg = Msg.replace("\n", " ");  Msg = Msg.replace("   ", " "); Msg = Msg.replace("  ", " ");
        sendSMS(splitNumber[MsgSentCount], Msg);
    }

    private void StartSendMsgSt(String phoneNumbers, String Msg)
    {
        SENT = "SMS_SENT";
        DELIVERED = "SMS_DELIVERED";
        MsgSentCount = 0;
        registerBroadCastReceivers();
        message = Msg;

        Diff = false;
        splitNumber = phoneNumbers.split("; *");

        SentDetails = SentDetails + "\nFile: " + MsgFileName.getName() + " with " +  splitNumber.length + " SMS(s)";
        Msg = Msg.replace("\n", " ");  Msg = Msg.replace("   ", " "); Msg = Msg.replace("  ", " ");
        sendSMS(splitNumber[MsgSentCount], Msg);
    }

    private void sendNextMessage()
    {
        if(thereAreSmsToSend())
        {
            String Msg =  message;
            if(Diff == true)
            {
                if (splitTitle[MsgSentCount] != "") Msg = Msg.replace("#Title", splitTitle[MsgSentCount]);
                if (splitFName[MsgSentCount] != "") Msg = Msg.replace("#FName", splitFName[MsgSentCount]);
                if (splitLName[MsgSentCount] != "") Msg = Msg.replace("#LName", splitLName[MsgSentCount]);
                if (Para1x != "") Msg = Msg.replace("#Para1", splitPara1[MsgSentCount]);
                if (Para2x != "") Msg = Msg.replace("#Para2", splitPara2[MsgSentCount]);
            }

            Msg = Msg.replace("\n", " ");  Msg = Msg.replace("   ", " "); Msg = Msg.replace("  ", " ");
            sendSMS(splitNumber[MsgSentCount], Msg);
        }
        else{
            Toast.makeText(getBaseContext(), "All SMS have been sent", Toast.LENGTH_SHORT).show();
            SentDetails = SentDetails + "\nAll file[" + Sel + "]SMS[" + MsgSentCount + "] have been sent.\n";
            txSMSMsg.setText(SentDetails );
            isPrint = false;
            MsgFileName.delete();

            if(Sel < fileNames.length) {
                MsgFileName = fileNames[Sel];
                OpenFileFromDownlodDir(dir, MsgFileName);
                Sel++;
                txSMSMsg.setText(SentDetails);
            }
            else {
                btnSendSMS.setEnabled(true);
                WriteToAxivFile(SentDetails);
            }
        }
    }

    private boolean thereAreSmsToSend() {
        return MsgSentCount < splitNumber.length;
    }

    private void sendSMS(String phoneNumber, String message)
    {
        if(phoneNumber.length() == 10) {
            SmsManager sms = SmsManager.getDefault();
            ArrayList<String> parts = sms.divideMessage(message);
            mMessageSentTotalParts = parts.size();

            Log.i("Message Count", "Message Count: " + mMessageSentTotalParts);

            ArrayList<PendingIntent> sentIntents = new ArrayList<PendingIntent>();
            ArrayList<PendingIntent> deliveryIntents = new ArrayList<PendingIntent>();

            PendingIntent sentPI = PendingIntent.getBroadcast(this, 0, new Intent(SENT), 0);
            PendingIntent deliveredPI = PendingIntent.getBroadcast(this, 0, new Intent(DELIVERED), 0);

            for (int j = 0; j < mMessageSentTotalParts; j++) {
                sentIntents.add(sentPI);
                deliveryIntents.add(deliveredPI);
            }

            mMessageSentParts = 0;
            sms.sendMultipartTextMessage(phoneNumber, null, parts, sentIntents, deliveryIntents);

            //Axiv = j.ToString() + " out of " + splitNumber.Length.ToString() + "\n" + AxivNo + "\n" + AxivTitle + "\n" + AxivFName + "\n"
                    //+ AxivMName + "\n" +  AxivLName + "\n" + AxivPara1 + "\n" + AxivPara2;

           // SentSMSCopy(fmLogin.PathEmailCopy, DiffEmail.ToString(), EmailTo, "", AxivTitle, AxivFName, AxivMName, AxivLName,
                   // AxivPara1, AxivPara2, AxivPara3, ImgName, ltAttachmentList, AxivEmailTo, EmailCC, EmailSubject, EmailBody);
           // Axiv = ""; AxivNo = ""; AxivTitle = ""; AxivFName = ""; AxivMName = ""; AxivLName = ""; AxivPara1 = ""; AxivPara2 = "";

        }
        else {
            txSMSMsg.setText(SentDetails);
            MsgSentCount++;
            sendNextMessage();
        }
        SentCount++;
        SentDetails = SentDetails + "\nFile[" + (Sel + 1) + "]SMS[" + (MsgSentCount+1) + "]Sendee[" + phoneNumber + "]";
    }
    private void registerBroadCastReceivers() {

        registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context arg0, Intent arg1) {
                switch (getResultCode()) {

                    case Activity.RESULT_OK:
                        mMessageSentParts++;
                        if (mMessageSentParts == mMessageSentTotalParts) {
                            MsgSentCount++;
                            sendNextMessage();
                        }
                        Toast.makeText(getBaseContext(), "SMS sent", Toast.LENGTH_SHORT).show();
                        if(isPrint == true )SentDetails = SentDetails + " SMS Sent\n";
                        break;
                    case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                        Toast.makeText(getBaseContext(), "Generic failure", Toast.LENGTH_SHORT).show();
                        SentDetails = SentDetails + " Generic failure";
                        break;
                    case SmsManager.RESULT_ERROR_NO_SERVICE:
                        Toast.makeText(getBaseContext(), "No service", Toast.LENGTH_SHORT).show();
                        SentDetails = SentDetails + "  No service";
                        break;
                    case SmsManager.RESULT_ERROR_NULL_PDU:
                        Toast.makeText(getBaseContext(), "Null PDU", Toast.LENGTH_SHORT).show();
                        SentDetails = SentDetails + " Null PDU";
                        break;
                    case SmsManager.RESULT_ERROR_RADIO_OFF:
                        Toast.makeText(getBaseContext(), "Radio off", Toast.LENGTH_SHORT).show();
                        SentDetails = SentDetails + " Radio off";
                        break;
                }
            }
        }, new IntentFilter(SENT));

        registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context arg0, Intent arg1) {
                switch (getResultCode()) {

                    case Activity.RESULT_OK:
                        Toast.makeText(getBaseContext(), "SMS delivered", Toast.LENGTH_SHORT).show();
                        if(isPrint == true )SentDetails = SentDetails + " Delivered\n";
                        break;
                    case Activity.RESULT_CANCELED:
                        Toast.makeText(getBaseContext(), "SMS not delivered", Toast.LENGTH_SHORT).show();
                        SentDetails = SentDetails + " not Delivered\n";
                        break;
                }
            }
        }, new IntentFilter(DELIVERED));
    }

    public static String getCurrentTimeStamp() {
        SimpleDateFormat sdfDate = new SimpleDateFormat("EEE dd/MM/yy HH:mm");//("dd/MM/yyyy HH:mm:ss"); "yyyyMMddHHmmssSS"
        Date now = new Date();
        String strDate = sdfDate.format(now);
        return strDate;
    }
    public void WriteToAxivFile(String sBody) {
        try {
            File SosiDBAxivFile = new File(new File(Environment.getExternalStorageDirectory(), SosiDBMsg), "SosiDBAxivFile.txt");
            FileWriter writer = new FileWriter(SosiDBAxivFile,true);
            writer.append(sBody+"\n");
            writer.flush();  writer.close();
            Toast.makeText(getBaseContext(), "Saved", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    /* Checks if external storage is available for read and write */
    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            File root = new File(Environment.getExternalStorageDirectory(), SosiDBMsg);
            if (!root.exists()) {
                root.mkdirs();
            }
            return true;
        }
        return false;
    }
    //end
}
