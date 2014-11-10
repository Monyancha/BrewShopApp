package com.brew.brewshop.fragments;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.support.v7.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.brew.brewshop.FragmentHandler;
import com.brew.brewshop.R;
import com.brew.brewshop.RecipeListView;
import com.brew.brewshop.ViewClickListener;
import com.brew.brewshop.storage.BrewStorage;
import com.brew.brewshop.storage.recipes.Recipe;

public class RecipeListFragment extends Fragment implements ViewClickListener,
        DialogInterface.OnClickListener,
        RecipeChangeHandler,
        ActionMode.Callback {

    @SuppressWarnings("unused")
    private static final String TAG = RecipeListFragment.class.getName();
    private static final String ACTION_MODE = "ActionMode";
    private static final String SELECTED_INDEXES = "Selected";
    private static final String SHOWING_ID = "ShowingId";

    private BrewStorage mStorage;
    private FragmentHandler mViewSwitcher;
    private ActionMode mActionMode;
    private RecipeListView mRecipeView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
        View rootView = inflater.inflate(R.layout.fragment_recipes, container, false);
        setHasOptionsMenu(true);

        checkResumeActionMode(bundle);
        mViewSwitcher.setTitle(getTitle());

        mStorage = new BrewStorage(getActivity());
        mRecipeView = new RecipeListView(getActivity(), rootView, mStorage, this);
        mRecipeView.drawRecipeList();
        if (bundle != null) {
            int id = bundle.getInt(SHOWING_ID, -1);
            mRecipeView.setShowing(id);
        }

        return rootView;
    }

    public String getTitle() {
        return getActivity().getResources().getString(R.string.homebrew_recipes);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mStorage.close();
    }

    @Override
    public void onSaveInstanceState(Bundle bundle) {
        super.onSaveInstanceState(bundle);
        bundle.putBoolean(ACTION_MODE, mActionMode != null);
        if (mRecipeView != null) {
            bundle.putInt(SHOWING_ID, mRecipeView.getShowingId());
        }
        if (mActionMode != null) {
            bundle.putIntArray(SELECTED_INDEXES, mRecipeView.getSelectedIds());
        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            mViewSwitcher = (FragmentHandler) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement " + FragmentHandler.class.getName());
        }
    }

    @Override
    public void onClick(View view) {
        boolean selected = (Boolean) view.getTag(R.integer.is_recipe_selected);
        int id = (Integer) view.getTag(R.integer.recipe_id);
        if (mActionMode != null) {
            mRecipeView.setSelected(id, !selected);
            if (mRecipeView.getSelectedCount() == 0) {
                mActionMode.finish();
            }
            updateActionBar();
        } else {
            if (mViewSwitcher != null) {
                Recipe recipe = mStorage.retrieveRecipes().findById(id);
                if (!mRecipeView.isShowing(recipe)) {
                    showRecipe(recipe);
                }
            } else {
                Log.d(TAG, "Recipe manager is not set");
            }
        }
    }

    @Override
    public boolean onLongClick(View view) {
        int id = (Integer) view.getTag(R.integer.recipe_id);
        if (mActionMode != null) {
            updateActionBar();
            return false;
        } else {
            startActionMode(new int[] {id});
        }
        return true;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.recipes_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        if (menuItem.getItemId() == R.id.action_new_recipe && canCreateRecipe()) {
            Recipe recipe = new Recipe();
            mStorage.createRecipe(recipe);
            mRecipeView.drawRecipeList();
            showRecipe(recipe);
            return true;
        }
        return false;
    }

    private void showRecipe(Recipe recipe) {
        int id = -1;
        if (recipe != null) {
            id = recipe.getId();
        }
        mRecipeView.setShowing(id);
        mViewSwitcher.showRecipeEditor(recipe);
    }

    @Override
    public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
        mActionMode = actionMode;
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
        menu.clear();
        int checked = mRecipeView.getSelectedCount();
        mActionMode.setTitle(getResources().getString(R.string.select_recipes));
        mActionMode.setSubtitle(checked + " " + getResources().getString(R.string.selected));

        MenuInflater inflater = actionMode.getMenuInflater();
        inflater.inflate(R.menu.context_menu, menu);

        boolean itemsChecked = (mRecipeView.getSelectedCount() > 0);
        mActionMode.getMenu().findItem(R.id.action_delete).setVisible(itemsChecked);
        mActionMode.getMenu().findItem(R.id.action_select_all).setVisible(!mRecipeView.areAllSelected());
        return true;
    }

    @Override
    public boolean onActionItemClicked(ActionMode actionMode, MenuItem menuItem) {
        switch (menuItem.getItemId()) {
            case R.id.action_select_all:
                Log.d(TAG, "Select all");
                mRecipeView.setAllSelected(true);
                updateActionBar();
                return true;
            case R.id.action_delete:
                int count = mRecipeView.getSelectedCount();
                String message;
                if (count > 1) {
                    message = String.format(getActivity().getResources().getString(R.string.delete_selected_recipes), count);
                } else {
                    message = String.format(getActivity().getResources().getString(R.string.delete_selected_recipe), count);
                }
                new AlertDialog.Builder(getActivity())
                        .setMessage(message)
                        .setPositiveButton(R.string.yes, this)
                        .setNegativeButton(R.string.no, null)
                        .show();
                return true;
            default:
                return false;
        }
    }

    @Override
    public void onDestroyActionMode(ActionMode actionMode) {
        mRecipeView.setAllSelected(false);
        mActionMode = null;
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        int deleted = deleteSelected();
        mActionMode.finish();
        toastDeleted(deleted);
    }

    private void checkResumeActionMode(Bundle bundle) {
        if (bundle != null) {
            if (bundle.getBoolean(ACTION_MODE)) {
                int[] selected = bundle.getIntArray(SELECTED_INDEXES);
                startActionMode(selected);
            }
        }
    }

    private void startActionMode(int[] selectedIds) {
        for (int i = 0; i < selectedIds.length; i++) {
            mRecipeView.setSelected(selectedIds[i], true);
        }
        ((ActionBarActivity) getActivity()).startSupportActionMode(this);
    }

    private boolean canCreateRecipe() {
        int maxRecipes = getActivity().getResources().getInteger(R.integer.max_recipes);
        if (mStorage.retrieveRecipes().size() >= maxRecipes) {
            String message = String.format(getActivity().getResources().getString(R.string.max_recipes_reached), maxRecipes);
            Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private int deleteSelected() {
        int[] selectedIds = mRecipeView.getSelectedIds();
        int showingId = mRecipeView.getShowingId();
        for (int i = 0; i < selectedIds.length; i ++) {
            int id = selectedIds[i];
            mStorage.deleteRecipe(mStorage.retrieveRecipes().findById(id));
            if (showingId == id) {
                showRecipe(null);
            }
        }
        mRecipeView.removeSelected();
        return selectedIds.length;
    }

    private void updateActionBar() {
        if (mActionMode != null) {
            mActionMode.invalidate();
        }
    }

    private void toastDeleted(int deleted) {
        Context context = getActivity();
        String message;
        if (deleted > 1) {
            message = String.format(context.getResources().getString(R.string.deleted_recipes), deleted);
        } else {
            message = context.getResources().getString(R.string.deleted_recipe);
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRecipeClosed(int recipeId) {
        if (mRecipeView != null) {
            mRecipeView.setShowing(-1);
        }
    }

    @Override
    public void onRecipeUpdated(int recipeId) {
        if (mRecipeView != null) {
            mRecipeView.setShowing(recipeId);
            mRecipeView.drawRecipeList();
        }
    }
}