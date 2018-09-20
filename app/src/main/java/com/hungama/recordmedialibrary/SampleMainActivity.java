package com.hungama.recordmedialibrary;

import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.hungama.recordmedialibrary.appBase.PlaybackActivity;
import com.hungama.recordmedialibrary.appBase.RecordMediaMainActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SampleMainActivity extends AppCompatActivity
{
    public static final String TAG = SampleMainActivity.class.getSimpleName();
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sample_layout);
        Intent i = new Intent(SampleMainActivity.this, RecordMediaMainActivity.class);
        startActivityForResult(i,1001);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult: "+requestCode+"  data : "+data);
        if(requestCode==1001&& data!=null){
          String path= data.getStringExtra("response");
            Intent intent = new Intent(SampleMainActivity.this, PlaybackActivity.class);
            intent.putExtra(PlaybackActivity.INTENT_NAME_VIDEO_PATH,path);
            startActivity(intent);
        }else {
            //TODO: Remove this  for actual
            finish();
        }
    }
}
