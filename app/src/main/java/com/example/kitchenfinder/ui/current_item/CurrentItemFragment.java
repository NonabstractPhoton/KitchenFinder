package com.example.kitchenfinder.ui.current_item;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.example.kitchenfinder.IngredientCard;
import com.example.kitchenfinder.MainActivity;
import com.example.kitchenfinder.R;
import com.example.kitchenfinder.databinding.FragmentCurrentItemBinding;
import com.example.kitchenfinder.ui.camera.CameraFragment;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

public class CurrentItemFragment extends Fragment {

    private FragmentCurrentItemBinding binding;
    private IngredientCard card;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        CurrentItemViewModel viewModel =
                new ViewModelProvider(this).get(CurrentItemViewModel.class);

        binding = FragmentCurrentItemBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        card = CameraFragment.getIngredientCard();

        if (!card.equals(IngredientCard.EMPTY))
        {
            Context context = getContext();
            if (context != null)
            {
                if (card.getImage() == null) // Cloud
                {
                    binding.imageContainer.setImageResource(R.drawable.loading);
                    StorageReference storageRef = FirebaseStorage.getInstance().getReference().child("users").child(MainActivity.getUser().getUid()).child("images");
                    storageRef.child(card.title+".jpg").getDownloadUrl().addOnCompleteListener(task ->{
                        if (task.isSuccessful())
                            Glide.with(context)
                                    .load(task.getResult())
                                    .circleCrop()
                                    .into(binding.imageContainer);
                    });

                }
                else // Local
                {
                    Glide.with(context)
                            .load(card.getImage())
                            .circleCrop()
                            .into(binding.imageContainer);
                }
            }

            binding.paragraphTextView.setText(card.text);
            binding.nameTextView.setText(card.title);
        }

        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}