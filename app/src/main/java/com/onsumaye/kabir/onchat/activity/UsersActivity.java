package com.onsumaye.kabir.onchat.activity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.JsonHttpResponseHandler;
import com.loopj.android.http.RequestParams;
import com.onsumaye.kabir.onchat.chat.ChatHandler;
import com.onsumaye.kabir.onchat.R;
import com.onsumaye.kabir.onchat.app.Config;
import com.onsumaye.kabir.onchat.app.StateHolder;
import com.onsumaye.kabir.onchat.dialogs.DeleteUserConfirmationDialog;
import com.onsumaye.kabir.onchat.helper.Common;
import com.onsumaye.kabir.onchat.storage.UserDatabaseHandler;
import com.onsumaye.kabir.onchat.users.User;
import com.onsumaye.kabir.onchat.users.UserAdapter;
import com.onsumaye.kabir.onchat.users.UserHandler;

import org.json.JSONException;
import org.json.JSONObject;

import cz.msebera.android.httpclient.Header;

public class UsersActivity extends AppCompatActivity
{
    public ListView usersListView;
    FloatingActionButton fab;
    UserAdapter userAdapter;
    EditText addUserEditText;
    public TextView loggedInAs;
    public ImageButton toolbar_deleteButton;

    BroadcastReceiver receiver;

    UserDatabaseHandler userDbHandler;

    ButtonState state = ButtonState.ADD;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_users);
        UserHandler.init(this);
        ChatHandler.myUsername = getIntent().getStringExtra("username");
        userDbHandler = new UserDatabaseHandler(this);
        userDbHandler.createDatabase();

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        setTitle("");
        setTitleColor(R.color.common_plus_signin_btn_text_light);
        userAdapter = new UserAdapter(getApplicationContext());

        usersListView = (ListView) findViewById(R.id.userListView);
        loggedInAs = (TextView) findViewById(R.id.loggedInAs);
        toolbar_deleteButton = (ImageButton) findViewById(R.id.deleteUserButton);
        usersListView.setAdapter(userAdapter);

        fab = (FloatingActionButton) findViewById(R.id.addUserButton);
        addUserEditText = (EditText) findViewById(R.id.addUserEditText);
        loggedInAs.setText(Html.fromHtml("You are logged in as <b>" + ChatHandler.myUsername + "</b>"));

        fab.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                toggleAddUserButton();
            }
        });

        //Update users from the database
        UserHandler.usersList.addAll(userDbHandler.getAllUsers());
        userAdapter.notifyDataSetChanged();
        usersListView.setAdapter(userAdapter);

        receiver = new BroadcastReceiver()
        {
            @Override
            public void onReceive(Context context, Intent intent)
            {
                if(intent.getAction().equals("refreshAdapterIntent"))
                    refreshAdapter();
                else if(intent.getAction().equals("addUser"))
                {
                    String username = intent.getStringExtra("username");
                    System.out.println(username);
                    addUser(username);
                }
            }
        };

        toolbar_deleteButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                DeleteUserConfirmationDialog dialog = new DeleteUserConfirmationDialog();
                dialog.show(getFragmentManager(), "OnChat");
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        LocalBroadcastManager.getInstance(this).registerReceiver((receiver),
                new IntentFilter("refreshAdapterIntent")
        );

        LocalBroadcastManager.getInstance(this).registerReceiver((receiver),
                new IntentFilter("addUser"));
    }

    @Override
    protected void onStop() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
        super.onStop();
    }

    private void toggleAddUserButton()
    {
        if(state == ButtonState.ADD)
        {
            addUserEditText.setText("");

            addUserEditText.setAlpha(0.0f);

            addUserEditText.setVisibility(View.VISIBLE);

            addUserEditText.animate()
                    .alpha(1.0f);

            fab.setImageResource(R.drawable.ic_done_white_18px);
            state = ButtonState.ACCEPT;
        }
        else
        {
            addUserEditText.animate().alpha(0.0f);
            addUserEditText.setVisibility(View.INVISIBLE);

            fab.setImageResource(R.drawable.ic_add_white_18px);
            if(!addUserEditText.getText().toString().equals(""))
            {
                //Send request to the server which will search the database for user existing
                String userToAdd = addUserEditText.getText().toString();
                userToAdd = userToAdd.toLowerCase();
                userToAdd = Common.capitalizeFirstLetter(userToAdd);
                if(!userToAdd.equalsIgnoreCase(ChatHandler.myUsername))
                {
                    addUser(userToAdd);
                }
                else Toast.makeText(getApplicationContext(), "You cannot add yourself!", Toast.LENGTH_SHORT).show();
            }
            state = ButtonState.ADD;
        }
    }

    private void addUser(String username)
    {
        AsyncHttpClient client = new AsyncHttpClient();
        RequestParams params = new RequestParams();

        params.put("username", username);

        client.post(Config.SERVER_IP + "/addUserId", params, new JsonHttpResponseHandler()
        {
            @Override
            public void onSuccess(int statusCode, Header[] headers, JSONObject response)
            {
                System.out.println("Status code: " + statusCode);
                try
                {
                    boolean userExists = response.getBoolean("exists");
                    if(userExists)
                    {
                        int id = response.getInt("id");
                        String username = response.getString("username");
                        String gcmId = response.getString("gcmId");

                        User user = new User(id, username, gcmId, 0);
                        if(!UserHandler.doesUserExist(user.getId()))
                        {
                            UserHandler.usersList.add(0, user);
                        }
                        else Toast.makeText(getApplicationContext(), "User already exists in the list.", Toast.LENGTH_SHORT).show();
                        userAdapter.notifyDataSetChanged();

                        //Save it to the database
                        userDbHandler.addUser(user);

                        usersListView.setAdapter(userAdapter);
                    }
                    else Toast.makeText(getApplicationContext(), "User does not exist.", Toast.LENGTH_SHORT).show();
                }
                catch (JSONException e)
                {
                    e.printStackTrace();
                }
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject response)
            {
                System.out.println("Got status code" + statusCode);
            }

        });
    }

    @Override
    public void onResume()
    {
        super.onResume();
        StateHolder.appState = StateHolder.AppState.USERS;
        userAdapter.notifyDataSetChanged();
        usersListView.setAdapter(userAdapter);
    }

    public void refreshAdapter()
    {
        userAdapter.notifyDataSetChanged();
        usersListView.setAdapter(userAdapter);
    }

    private enum ButtonState
    {
        ADD, ACCEPT
    }

    @Override
    public void onBackPressed()
    {
        //Logging out
        UserHandler.usersList.clear();

        new android.app.AlertDialog.Builder(this)
                .setMessage("Are you sure you want to log out?")
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        finish();
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        //Do nothing
                    }
                })
                .show();
    }
}
