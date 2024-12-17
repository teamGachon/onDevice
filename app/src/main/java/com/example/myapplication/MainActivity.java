package com.example.myapplication;
import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;


public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_PERMISSION_CODE = 100;
    private static final int SAMPLE_RATE = 48000;
    private static final int AUDIO_BUFFER_SIZE = SAMPLE_RATE * 2; // 2초 데이터

    private Interpreter tflite;
    private TextView resultTextView;
    private boolean isRecording = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        resultTextView = findViewById(R.id.resultTextView);

        // 권한 확인
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_PERMISSION_CODE);
        } else {
            initTFLite();
            startAudioRecording();
        }
    }

    private void initTFLite() {
        try {
            // TFLite 모델 로드
            FileInputStream fis = new FileInputStream(getAssets().openFd("car_detection_raw_audio_model.tflite").getFileDescriptor());
            FileChannel fileChannel = fis.getChannel();
            long startOffset = getAssets().openFd("car_detection_raw_audio_model.tflite").getStartOffset();
            long declaredLength = getAssets().openFd("car_detection_raw_audio_model.tflite").getDeclaredLength();
            ByteBuffer modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
            tflite = new Interpreter(modelBuffer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startAudioRecording() {
        new Thread(() -> {
            try {
                AudioRecord recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                        SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT, AUDIO_BUFFER_SIZE);

                short[] audioData = new short[AUDIO_BUFFER_SIZE / 2];
                recorder.startRecording();

                while (isRecording) {
                    int result = recorder.read(audioData, 0, audioData.length);
                    if (result > 0) {
                        runOnUiThread(() -> detectSound(audioData));
                    }
                }

                recorder.stop();
                recorder.release();
            } catch (SecurityException e) {
                e.printStackTrace();
                runOnUiThread(() -> resultTextView.setText("Permission Denied"));
            }
        }).start();
    }
    private void detectSound(short[] audioData) {
        // 입력 데이터를 모델이 기대하는 shape [1, 96000, 1]로 변환
        float[][][] input = new float[1][96000][1];
        int length = Math.min(audioData.length, 96000); // 데이터 길이가 96000 이하일 경우

        for (int i = 0; i < length; i++) {
            input[0][i][0] = audioData[i] / 32768.0f; // 정규화 및 채널 추가
        }

        // 남은 부분 0으로 패딩
        for (int i = length; i < 96000; i++) {
            input[0][i][0] = 0.0f;
        }

        // 모델 실행 및 예측
        float[][] output = new float[1][1];
        tflite.run(input, output);

        // 예측 결과 표시
        String result = output[0][0] < 0.5 ? "Car Detected" : "No Car Sound";
        resultTextView.setText(result);
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initTFLite();
                startAudioRecording();
            } else {
                resultTextView.setText("Audio recording permission is required.");
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isRecording = false;
        if (tflite != null) {
            tflite.close();
        }
    }
}