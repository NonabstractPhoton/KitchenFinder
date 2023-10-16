package com.example.kitchenfinder.ui.dashboard;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.kitchenfinder.IngredientCard;
import com.example.kitchenfinder.IngredientData;
import com.example.kitchenfinder.MainActivity;
import com.example.kitchenfinder.R;
import com.example.kitchenfinder.SignOutActivity;
import com.example.kitchenfinder.databinding.FragmentDashboardBinding;
import com.example.kitchenfinder.ui.camera.CameraFragment;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

public class DashboardFragment extends Fragment {

    private RecyclerView recyclerView;
    private IngredientAdapter ingredientAdapter;
    private RecyclerView.LayoutManager layoutManager;
    private FirebaseDatabase database;
    private DatabaseReference favoritesRef;
    private FirebaseStorage storage;
    private StorageReference storageRef;
    static ArrayList<IngredientCard> localCards = new ArrayList<>();
    static int currentItemIndex = -1;

    private CloudExecutor cloudExecutor;


    private FragmentDashboardBinding binding;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        DashboardViewModel viewModel =
                new ViewModelProvider(this).get(DashboardViewModel.class);

        binding = FragmentDashboardBinding.inflate(inflater, container, false);
        View root = binding.getRoot();
        recyclerView = binding.recyclerView;
        cloudExecutor = new CloudExecutor();

        binding.accountImageView.setOnClickListener(view ->{
            Context context = getContext();
            if (context != null)
            {
                Intent intent = new Intent(getContext(), SignOutActivity.class);
                startActivity(intent);
            }
        });

        binding.gearImageView.setOnClickListener(view ->{
            Context context = getContext();
            if (context != null)
            {
                Toast.makeText(context, "This feature is not yet supported", Toast.LENGTH_LONG).show();
            }
        });

        new Thread(this::initializeRecyclerView).start();

        return root;
    }

    private void initializeRecyclerView()
    {
        database = FirebaseDatabase.getInstance();
        storage = FirebaseStorage.getInstance();

        FirebaseUser user = MainActivity.getUser();

        // favoritesRef points to a List<Object> with underlying type List<IngredientData>
        favoritesRef = database.getReference().child("users").child(user.getUid()).child("favorites");
        storageRef = storage.getReference().child("users").child(user.getUid()).child("images");

        favoritesRef.get().addOnCompleteListener(task -> {
            if (!task.isSuccessful())
                Log.w("TEST", "Failed to get favorites list from database");
            else
            {
                // Deep clone localCards into cards
                ArrayList<IngredientCard> cards = new ArrayList<IngredientCard>();
                for (IngredientCard localCard : localCards) {
                    try {
                        cards.add(((IngredientCard) localCard.clone()));
                    } catch (CloneNotSupportedException e) {
                        throw new RuntimeException(e);
                    }
                }
                // Turn database data into cards and denote them as favorites. Images are left null since they will be dynamically loaded
                ArrayList<HashMap<String, String>> data = ((ArrayList) task.getResult().getValue());
                if (data != null)
                    for(HashMap<String, String> map : data)
                    {
                        IngredientCard card = new IngredientCard(map.get("title"), map.get("text"));
                        card.setFavorite(true);
                        cards.add(card);
                    }

                Handler h = new Handler(Looper.getMainLooper());
                h.post(() -> {
                    layoutManager = new LinearLayoutManager(getActivity());
                    recyclerView.setLayoutManager(layoutManager);
                    ingredientAdapter = new IngredientAdapter(cards);
                    recyclerView.setAdapter(ingredientAdapter);
                });
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    public static void addToLocalCards(IngredientCard card)
    {
        localCards.add(card);
    }
    public static void removeFromLocalCards(IngredientCard card) {localCards.remove(card);}

    @Override
    public void onPause() {
        super.onPause();
        cloudExecutor.runPosted();
    }

    class IngredientAdapter extends RecyclerView.Adapter<IngredientHolder>
    {
        private final List<IngredientCard> data;
        public IngredientAdapter(List<IngredientCard> data)
        {
            this.data = data;
        }

        @NonNull
        @Override
        public IngredientHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.view_ingredient_holder, parent, false);

            return new IngredientHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull IngredientHolder holder, int position) {

            final IngredientCard card = data.get(position); // Value, not reference, must use list to modify underlying data. Final is just a reminder of that

            if (CameraFragment.getIngredientCard().equals(card))
            {
                holder.setCurrentItemState(true);
                if (currentItemIndex != -1)
                    recyclerView.post(() ->{
                        notifyItemChanged(currentItemIndex);
                    });
                currentItemIndex = holder.getAdapterPosition();
            }
            else holder.setCurrentItemState(false); // Needs this clause to work with notifyItemChanged

            // Is not shortened to avoid unnecessary resource reassignments
            if (card.getFavorite())
                holder.setFavoriteState(true);

            holder.nameText.setText(card.title);

            Context context = getContext();
            if (context != null)
            {
                if (card.getImage() == null) // Cloud
                {
                    storageRef.child(card.title+".jpg").getDownloadUrl().addOnCompleteListener(task ->{
                        if (task.isSuccessful())
                            Glide.with(context)
                                    .load(task.getResult())
                                    .circleCrop()
                                    .into(holder.ingredientImageHolder);
                    });
                }
                else //Local
                {
                    Glide.with(context)
                            .load(card.getImage())
                            .circleCrop()
                            .into(holder.ingredientImageHolder);
                }
            }

            holder.currentItemIcon.setOnClickListener(view ->{;
                CameraFragment.setIngredientCard(card);

                holder.setCurrentItemState(true);
                if (currentItemIndex != -1)
                    notifyItemChanged(currentItemIndex);

                currentItemIndex = holder.getAdapterPosition();
            });

            holder.favoriteIcon.setOnClickListener(view ->{
                if (card.getFavorite())
                {
                    data.get(holder.getAdapterPosition()).setFavorite(false);
                    holder.setFavoriteState(false);
                    cloudExecutor.scheduleRemove(card);
                }
                else
                {
                    data.get(holder.getAdapterPosition()).setFavorite(true);
                    holder.setFavoriteState(true);
                    cloudExecutor.scheduleAdd(card);
                }
            });

        }

        @Override
        public int getItemCount() {
            return data.size();
        }
    }

    class IngredientHolder extends RecyclerView.ViewHolder
    {
        private final TextView nameText;
        private final ImageView ingredientImageHolder, favoriteIcon, currentItemIcon;

        public IngredientHolder(@NonNull View itemView) {
            super(itemView);
            nameText = itemView.findViewById(R.id.ingredientNameTextView);
            ingredientImageHolder = itemView.findViewById(R.id.ingredientImageView);
            favoriteIcon = itemView.findViewById(R.id.favoriteIcon);
            currentItemIcon = itemView.findViewById(R.id.currentItemIcon);
        }

        // Utilities to make code more readable in adapter
        public void setFavoriteState(boolean filled) {
            favoriteIcon.setImageResource(filled ? R.drawable.star_filled : R.drawable.star_empty);
        }

        public void setCurrentItemState(boolean filled) {
            currentItemIcon.setImageResource(filled ? R.drawable.current_item_filled : R.drawable.current_item);
        }
    }

    // Batches cloud requests and handles overlaps
    class CloudExecutor implements Executor
    {
        final ArrayList<IngredientCard> additionQueue= new ArrayList<>();
        final ArrayList<IngredientData> removalQueue = new ArrayList<>();
        @Override
        public void execute(Runnable command) {
            new Thread(command).start();
        }

        public void scheduleAdd(IngredientCard card)
        {
            if (!removalQueue.remove(card)) // If did not remove it from scheduled removals (to deal with toggling of favorite status)
            {
                additionQueue.add(card);
            }
        }

        public void scheduleRemove(IngredientCard card)
        {
            if (!additionQueue.remove(card)) // Same logic as earlier but inverse
            {
                removalQueue.add(new IngredientData(card.title, card.text));
            }
        }


        public void runPosted() {
            Log.d("TEST", "Running Posted Cloud Changes");

            if (additionQueue.size() == 0 && removalQueue.size() == 0)
                return;

            new Thread(() ->{
                ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(additionQueue.size());

                // Removal must happen before addition, otherwise removed values would still appear in the database
                for (IngredientData card : removalQueue)
                {
                    executor.submit(() ->
                    {
                        storageRef.child(card.title+".jpg").delete();
                    });
                }

                favoritesRef.get().addOnCompleteListener(task -> {

                    List<IngredientData> tbr = new ArrayList<>();
                    List<HashMap<String, String>> favs = ((ArrayList) task.getResult().getValue());
                    List<IngredientData> favorites = new ArrayList<>();
                    if (favs != null)
                    {
                        for (HashMap<String, String> map : favs)
                        {
                            favorites.add(new IngredientData(map.get("title"), map.get("text")));
                        }
                    }
                    for (IngredientData fav : favorites) {
                        if (removalQueue.contains(fav))
                            tbr.add(fav);
                    }
                    favorites.removeAll(tbr);


                    // Addition
                    Collection<Future<?>> addFutures = new LinkedList<Future<?>>();

                    for (IngredientCard card : additionQueue)
                    {
                        addFutures.add(executor.submit(() ->
                        {
                            // Prevent duplicates
                            removeFromLocalCards(card);
                            storageRef.child(card.title+".jpg").putStream(bitmapToInputStream(card.getImage()));
                            favorites.add(new IngredientData(card.title, card.text));
                        }));
                    }

                    // Delays execution until all tasks complete
                    for (Future<?> future:addFutures)
                    {
                        try
                        {
                            future.get();
                        } catch (Exception e)
                        {
                            Log.e("TEST", "Exception in addition futures: "+e);
                        }
                    }

                    favoritesRef.setValue(favorites);

                });

            }).start();
        }

    }

    private InputStream bitmapToInputStream(Bitmap bitmap)
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out);
        return new ByteArrayInputStream(out.toByteArray());
    }
}