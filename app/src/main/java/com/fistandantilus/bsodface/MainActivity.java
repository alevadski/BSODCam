package com.fistandantilus.bsodface;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;

import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    public static final int REQUEST_PHOTO_CAPTURE = 1;
    public static final int REQUEST_PICK_PHOTO = 2;

    public static final int MODE_WAIT_PHOTO = -1;
    public static final int MODE_READY = -2;
    public static final int MODE_PROCESSING = -3;

    private int currentMode = MODE_WAIT_PHOTO;

    private Button processButton;
    private Button shareButton;
    private ImageView photoView;
    private FrameLayout processingLayout;

    private Bitmap photo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        processButton = (Button) findViewById(R.id.process);
        shareButton = (Button) findViewById(R.id.share);
        photoView = (ImageView) findViewById(R.id.photoView);
        processingLayout = (FrameLayout) findViewById(R.id.processingLayout);

        changeMode(MODE_WAIT_PHOTO);
    }

    private void changeMode(int mode) {
        if (currentMode == mode) return;

        switch (mode) {
            case MODE_WAIT_PHOTO:
                processButton.setEnabled(false);
                photoView.setImageResource(R.drawable.bsod);
                shareButton.setVisibility(View.GONE);
                processingLayout.setVisibility(View.GONE);
                break;
            case MODE_READY:
                if (photo == null) return;
                processButton.setEnabled(true);
                processingLayout.setVisibility(View.GONE);
                photoView.setImageBitmap(photo);
                shareButton.setVisibility(View.VISIBLE);
                break;
            case MODE_PROCESSING:
                processingLayout.setVisibility(View.VISIBLE);
                photoView.setImageBitmap(photo);
                shareButton.setVisibility(View.GONE);
                break;
        }
    }

    public void onPickPhotoClick(View view) {
        showPhotoPickerDialog();
    }

    private void showPhotoPickerDialog() {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);

        dialogBuilder.setTitle(getString(R.string.choose_source));
        dialogBuilder.setItems(getResources().getStringArray(R.array.photo_sources), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case 0:
                        takePhotoFromCamera();
                        break;
                    case 1:
                        choosePhotoFromGallery();
                        break;
                }
            }
        });

        dialogBuilder.create().show();
    }

    private void choosePhotoFromGallery() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, getString(R.string.choose_photo)), REQUEST_PICK_PHOTO);
    }

    private void takePhotoFromCamera() {
        Intent takePhotoIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePhotoIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(takePhotoIntent, REQUEST_PHOTO_CAPTURE);
        }
    }

    public void onProcessClick(View view) {
        if (photo == null) return;
        new Processor().execute();
    }

    public void onShareClick(View view) {
        Toast.makeText(this, "Coming soon...", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            Bitmap bitmap = null;

            switch (requestCode) {
                case REQUEST_PHOTO_CAPTURE:
                    Bundle extras = data.getExtras();
                    bitmap = (Bitmap) extras.get("data");
                    break;
                case REQUEST_PICK_PHOTO:
                    Uri uri = data.getData();

                    try {
                        bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
                    } catch (IOException e) {
                        Toast.makeText(this, getString(R.string.cant_open_photo), Toast.LENGTH_SHORT).show();
                    }
                    break;
            }
            if (bitmap != null) {
                photo = bitmap;
                changeMode(MODE_READY);
            }
        }
    }

    class Processor extends AsyncTask<Void, Void, Void> {

        Bitmap tempBitmap;

        @Override
        protected void onPreExecute() {
            Toast.makeText(MainActivity.this, "Processing...", Toast.LENGTH_SHORT).show();
            changeMode(MODE_PROCESSING);
        }

        @Override
        protected Void doInBackground(Void... params) {

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inMutable = true;

            Bitmap bsodBitmap = BitmapFactory.decodeResource(getApplicationContext().getResources(),
                    R.drawable.bsod, options);

            Paint myRectPaint = new Paint();
            myRectPaint.setStrokeWidth(5);
            myRectPaint.setColor(Color.RED);
            myRectPaint.setAlpha(200);
            myRectPaint.setStyle(Paint.Style.STROKE);

            tempBitmap = Bitmap.createBitmap(photo.getWidth(), photo.getHeight(), Bitmap.Config.RGB_565);
            Canvas tempCanvas = new Canvas(tempBitmap);
            tempCanvas.drawBitmap(photo, 0, 0, null);

            FaceDetector faceDetector = new FaceDetector.Builder(getApplicationContext())
                    .setTrackingEnabled(false)
                    .build();

            if (!faceDetector.isOperational()) {
                new AlertDialog.Builder(MainActivity.this).setMessage("Could not set up the face detector!").show();
                return null;
            }

            Frame frame = new Frame.Builder().setBitmap(photo).build();
            SparseArray<Face> faces = faceDetector.detect(frame);

            Log.d("DETECTOR", "FACES: " + faces.size());

            for (int i = 0; i < faces.size(); i++) {
                Face thisFace = faces.valueAt(i);
                float x1 = thisFace.getPosition().x;
                float y1 = thisFace.getPosition().y;
                float x2 = x1 + thisFace.getWidth();
                float y2 = y1 + thisFace.getHeight();
                tempCanvas.drawBitmap(bsodBitmap, null, new RectF(x1, y1, x2, y2), myRectPaint);
            }

            faceDetector.release();
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {

            photo = tempBitmap;
            changeMode(MODE_READY);
        }
    }
}
