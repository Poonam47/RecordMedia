package com.hungama.recordmedialibrary;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

import com.hungama.recordmedialibrary.appBase.RecordMediaMainActivity;

public class SampleMainActivity extends AppCompatActivity
{
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sample_layout);
        Intent i = new Intent(SampleMainActivity.this, RecordMediaMainActivity.class);
        startActivity(i);
    }
}
