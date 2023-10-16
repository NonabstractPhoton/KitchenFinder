package com.example.kitchenfinder;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.style.UnderlineSpan;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class LoginActivity extends AppCompatActivity {

    private Button button;
    private TextView swap;
    private EditText emailBox, passwordBox;
    private FirebaseAuth mAuth;

    boolean login = true;

    public static final String USER = "firebaseuser";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        button = findViewById(R.id.button_activate);
        swap = findViewById(R.id.textView_Swap);
        emailBox = findViewById(R.id.editText_EmailAddress);
        passwordBox = findViewById(R.id.editText_Password);
        mAuth = FirebaseAuth.getInstance();

        button.setOnClickListener(view ->
        {
            String email = emailBox.getText().toString(), password = passwordBox.getText().toString();

            if (email.isEmpty() || password.isEmpty())
            {
                Toast.makeText(getApplicationContext(), "Please fill out the required fields.", Toast.LENGTH_LONG).show();
                return;
            }

            if (login)
                login(email, password);
            else
                register(email, password);

        });

        swap.setOnClickListener(view ->
        {
            login = !login;
            button.setText(login ? R.string.login : R.string.register);
            swapText();
        });
    }

    @Override
    public void onStart()
    {
        super.onStart();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if(currentUser != null)
            launchMain();
    }

    void swapText() // Changes the text of the "Login?" or "Register?" prompt
    {
        SpannableString s = new SpannableString(getString(login ? R.string.switch_to_register : R.string.switch_to_login));
        s.setSpan(new UnderlineSpan(), 0, getString(login ? R.string.switch_to_register : R.string.switch_to_login).length(), 0);
        swap.setText(s);
    }

    void launchMain()
    {
        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        intent.putExtra(USER, mAuth.getCurrentUser());
        startActivity(intent);
        finish();
    }

    void login(String email, String password)
    {
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            // Sign in success, update UI with the signed-in user's information
                            Log.d("TEST", "signInWithEmail:success");
                            launchMain();
                        } else {
                            // If sign in fails, display a message to the user.
                            Log.w("TEST", "signInWithEmail:failure", task.getException());
                            Toast.makeText(getApplicationContext(), "Authentication failed.",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    void register(String email, String password)
    {
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        // Sign in success, update UI with the signed-in user's information
                        Log.d("TEST", "createUserWithEmail:success");
                        launchMain();
                    } else {
                        // If sign in fails, display a message to the user.
                        Log.w("TEST", "createUserWithEmail:failure", task.getException());
                        Toast.makeText(getApplicationContext(), "Authentication failed.",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

}