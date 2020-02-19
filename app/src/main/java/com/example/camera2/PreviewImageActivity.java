package com.example.camera2;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.os.Bundle;
import android.os.Environment;
import android.widget.ImageView;

import java.io.File;
import java.io.IOException;

public class PreviewImageActivity extends AppCompatActivity {
    private ImageView imgView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preview_image);
        imgView = findViewById(R.id.imgView);

    }

    @Override
    protected void onResume() {
        super.onResume();
        Intent intent = getIntent();
       String path= intent.getStringExtra("image");

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap = BitmapFactory.decodeFile(path, options);

        imgView.setImageBitmap(bitmap);


    }


}
