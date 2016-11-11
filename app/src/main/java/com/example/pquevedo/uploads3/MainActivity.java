package com.example.pquevedo.uploads3;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.ButtonBarLayout;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import static android.os.Environment.getExternalStoragePublicDirectory;

public class MainActivity extends Activity implements View.OnClickListener {

    Intent cameraIntent;
    Bitmap bmpPhoto;
    final static int cons = 0;
    Button buttonTakePhoto, downloadPhoto;
    private String photoDirectory;
    private TransferUtility transferUtility;
    TextView textViewPercentage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        init();

        // Initialize the Amazon Cognito credentials provider
        CognitoCachingCredentialsProvider credentialsProvider = new CognitoCachingCredentialsProvider(
                getApplicationContext(),
                "us-west-2:af389214-4901-4ebd-b384-4fceaedb5a58", // Identity Pool ID
                Regions.US_WEST_2 // Region
        );

        AmazonS3 s3 = new AmazonS3Client(credentialsProvider);
        transferUtility = new TransferUtility(s3, getApplicationContext());

    }

    private void init(){
        textViewPercentage = (TextView)findViewById(R.id.percentage);

        buttonTakePhoto = (Button)findViewById(R.id.buttonTakePhoto);
        buttonTakePhoto.setOnClickListener(this);

        downloadPhoto = (Button)findViewById(R.id.downloadPhoto);
        downloadPhoto.setOnClickListener(this);

    }


    private void takePhoto(){
        cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        startActivityForResult(cameraIntent, cons);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(resultCode == Activity.RESULT_OK){
            Bundle photoBundle = data.getExtras();
            bmpPhoto = (Bitmap)photoBundle.get("data");
            //Set photo to image view
            Drawable img = new BitmapDrawable(getResources(), bmpPhoto);
            img.setBounds( 0, 0, 130, 130 );  // set the image size
            buttonTakePhoto.setCompoundDrawables( img, null, null, null );
            //Save photo process
            File direct = new File(getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString()+"/aws_photos");
            if (!direct.exists()){
                direct.mkdirs();
            }

            final String fileName = "Invoice-"+String.valueOf(System.currentTimeMillis())+".jpg";
            File file = new File(direct, fileName);
            if (file.exists()){
                file.delete();
            }else {
                //Compress photo and save
                try {
                    FileOutputStream out = new FileOutputStream(file);
                    if(bmpPhoto.compress(Bitmap.CompressFormat.JPEG, 100, out)){
                        out.close();
                        photoDirectory = "file:"+file.getAbsolutePath();
                        //Upload photo
                        TransferObserver observer = transferUtility.upload("dailyapp-userfiles-mobilehub-1902723423/public", fileName, file);
                        observer.setTransferListener(new TransferListener() {
                            @Override
                            public void onStateChanged(int id, TransferState state) {
                                if(state.equals(TransferState.COMPLETED)){
                                    photoDirectory = "https://"+"dailyapp-userfiles-mobilehub-1902723423"+".s3.amazonaws.com/"+fileName;

                                }
                            }

                            @Override
                            public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {
                                int percentage = (int)(bytesCurrent/bytesTotal * 100);
                                textViewPercentage.setText(String.valueOf(percentage)+"%");
                            }

                            @Override
                            public void onError(int id, Exception ex) {
                                Log.d("Error: ",ex.getMessage());
                            }
                        });

                    }
                    //sendBroadcast(new Intent(Intent.ACTION_MEDIA_MOUNTED, Uri.parse("file://"+Environment.getExternalStorageState())));
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.buttonTakePhoto){
            takePhoto();
        }else if(view.getId() == R.id.downloadPhoto){
            File direct = new File(getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString()+"/aws_photos");

            final File file = new File(direct, "Invoice-1478885415167.jpg");
            TransferObserver transferObserver = transferUtility.download("dailyapp-userfiles-mobilehub-1902723423/public", "Invoice-1478885415167.jpg", file);
            transferObserver.setTransferListener(new TransferListener(){

                @Override
                public void onStateChanged(int id, TransferState state) {
                    if (state.equals(TransferState.COMPLETED)){
                        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());

                        Drawable img = new BitmapDrawable(getResources(), bitmap);
                        img.setBounds( 0, 0, 130, 130 );  // set the image size
                        downloadPhoto.setCompoundDrawables( img, null, null, null );

                    }
                    // do something
                }

                @Override
                public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {
                    //int percentage = (int) (bytesCurrent/bytesTotal * 100);
                    //Display percentage transfered to user
                }

                @Override
                public void onError(int id, Exception ex) {
                    Log.d("Error: ", ex.getMessage());
                }

            });
        }
    }
}
