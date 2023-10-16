package com.example.kitchenfinder.ui.camera;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.kitchenfinder.IngredientCard;
import com.example.kitchenfinder.MainActivity;
import com.example.kitchenfinder.databinding.FragmentCameraBinding;
import com.example.kitchenfinder.ui.dashboard.DashboardFragment;
import com.google.android.gms.tasks.Task;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

public class CameraFragment extends Fragment {

    private FragmentCameraBinding binding;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private PreviewView previewView;
    private ImageCapture imageCapture;
    private Camera camera;
    static IngredientCard card = IngredientCard.EMPTY;
    private FirebaseFunctions functions;
    public static final int IMAGE_WIDTH = 640, IMAGE_HEIGHT = 480, LABEL_COUNT = 5;


    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        CameraViewModel dashboardViewModel =
                new ViewModelProvider(this).get(CameraViewModel.class);

        binding = FragmentCameraBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        previewView = binding.previewView;

        if (!(ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED))
        {
            Toast.makeText(requireContext(), "Camera use is required for this app to function.", Toast.LENGTH_LONG).show();
            new Thread(()->{
                try {
                    Thread.sleep(3500); // Actual length of Toast.LENGTH_LONG
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                requestCameraPermission();
            }).start();
        }
        else
            requestCameraPermission();

        functions = FirebaseFunctions.getInstance();

        cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext());
        cameraProviderFuture.addListener(() -> {
            try
            {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                cameraProvider.unbindAll();
                bindPreview(cameraProvider);
            }
            catch (Exception e)
            {
                Log.e("TEST", e.toString());
            }
        }, ContextCompat.getMainExecutor(requireContext()));

        binding.imageCaptureButton.setOnClickListener(view->{
            imageCapture.takePicture(ContextCompat.getMainExecutor(requireContext()), new ImageCapture.OnImageCapturedCallback() {
                @Override
                @androidx.camera.core.ExperimentalGetImage
                public void onCaptureSuccess(@NonNull ImageProxy imageProxy) { // TODO
                    super.onCaptureSuccess(imageProxy);
                    new Thread(() ->{
                        createInfoCard(imageProxy);
                        imageProxy.close(); // Needed to prevent memory leak
                    }).start();
                }

                @Override
                public void onError(@NonNull ImageCaptureException exception) {
                    super.onError(exception);
                    Log.w("TEST", "Image Failed to Capture");
                }
            });
        });

        return root;
    }

    void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder()
                .build();

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        imageCapture = new ImageCapture.Builder()
                        .setTargetRotation(previewView.getDisplay().getRotation())
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .build();

        camera = cameraProvider.bindToLifecycle(this, cameraSelector, imageCapture, preview);
    }

    private void requestCameraPermission()
    {
        requestPermissions(new String[] { Manifest.permission.CAMERA },
                0);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    public static IngredientCard getIngredientCard() { return card; }
    public static void setIngredientCard(IngredientCard newCard) {card = newCard;}

    @androidx.camera.core.ExperimentalGetImage
    private void createInfoCard(ImageProxy imageProxy)
    {
        AtomicReference<String> title = new AtomicReference<>(), text = new AtomicReference<>();
        Bitmap imageBitmap;

        // Gets image bytes, encodes it to Base64 for the Vision API request, and into a Bitmap for in-app use
        byte[] imageBytes = MainActivity.imageProxyToByteArray(imageProxy);
        imageBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
        String base64encoded = Base64.encodeToString(imageBytes, Base64.NO_WRAP);
        Matrix rotation = new Matrix();
        rotation.postRotate(90);
        imageBitmap = Bitmap.createBitmap(imageBitmap, 0, 0, imageBitmap.getWidth(), imageBitmap.getHeight(), rotation, true);

        // TODO - REMOVE FOR TESTING WHICH REQUIRES NEW SCANS / API TESTING

        // Creates data object to pass over to cloud function as a request to analyze the image

        JsonObject request = new JsonObject();
        JsonObject image = new JsonObject();
        image.add("content", new JsonPrimitive(base64encoded));
        request.add("image", image);
        JsonObject feature = new JsonObject();
        feature.add("maxResults", new JsonPrimitive(LABEL_COUNT));
        feature.add("type", new JsonPrimitive("LABEL_DETECTION"));
        JsonArray features = new JsonArray();
        features.add(feature);
        request.add("features", features);

        // API request
        Bitmap finalImageBitmap = imageBitmap;
        annotateImage(request.toString())
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Log.w("TEST", "Image annotation failed: " + task.getException());
                    } else {
                        JsonArray labelJSONS = task.getResult().getAsJsonArray().get(0).getAsJsonObject().get("labelAnnotations").getAsJsonArray();

                        Log.d("TEST", "Image annotation successful");
                        ArrayList<String> labels = new ArrayList<String>();
                        for (JsonElement label : labelJSONS) {
                            JsonObject labelObj = label.getAsJsonObject();
                            labels.add(labelObj.get("description").getAsString());

                            // The following are for testing purposes, delete for final build
                            float score = labelObj.get("score").getAsFloat();
                            Log.d("TEST", String.format("Text: %s, Score: %s", labels.get(labels.size()-1), score));
                        }

                        generateParagraphText(labelJSONS.toString()).addOnCompleteListener(gptTask ->{
                            if (!gptTask.isSuccessful())
                                Log.w("TEST", "GPT text generation failed: "+gptTask.getException());
                            else {
                                Log.d("TEST", "GPT text generation successful");
                                Log.d("TEST", "Paragraph Text: "+gptTask.getResult().substring(1).trim());
                                text.set(gptTask.getResult().substring(1).trim());
                                title.set(getFocus(labels, text.get()));
                                card = new IngredientCard(title.get(), text.get(), finalImageBitmap);
                                DashboardFragment.addToLocalCards(card);
                                callCurrentItemCreationAlert();
                            }
                        });
                    }
                });

    }

    private Task<JsonElement> annotateImage(String requestJson) {
        return functions
                .getHttpsCallable("annotateImage")
                .call(requestJson)
                .continueWith(task -> JsonParser.parseString(new Gson().toJson(task.getResult().getData())));
    }

    private Task<String> generateParagraphText(String requestArray) {
        return functions
                .getHttpsCallable("generateParagraphText")
                .call(requestArray)
                .continueWith(task -> String.valueOf(task.getResult().getData()));
    }

    private String getFocus(ArrayList<String> labels, String paragraph) // Returns most specific (least scoring) label that the GPT paragraph discusses
    {
        String focus = null;
        for (String l : labels)
            if (paragraph.contains(l))
                focus = l;
        return (focus == null ? labels.get(0) : focus); // If GPT failed to discuss a label, return the first label, else follow normal behavior
    }

    private void callCurrentItemCreationAlert()
    {
        Objects.requireNonNull(getActivity()).runOnUiThread(() ->{
            Toast.makeText(requireContext(), "Ingredient Profile Complete!", Toast.LENGTH_LONG).show();
        });
    }

    void spoofGenerateText(Bitmap imageBitmap) // TODO - DELETE
    {
        card = new IngredientCard("Elephant Garlic", "Elephant garlic is a type of garlic that is larger than regular garlic. It has a stronger flavor and is used in some types of cooking. You can cook with elephant garlic by boiling it or frying it.", imageBitmap);
        DashboardFragment.addToLocalCards(card);
        callCurrentItemCreationAlert();
    }

}