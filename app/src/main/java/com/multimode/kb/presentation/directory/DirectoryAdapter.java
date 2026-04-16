package com.multimode.kb.presentation.directory;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.multimode.kb.R;
import com.multimode.kb.domain.entity.DirectoryStatus;
import com.multimode.kb.domain.entity.TrackedDirectory;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DirectoryAdapter extends RecyclerView.Adapter<DirectoryAdapter.ViewHolder> {

    private List<TrackedDirectory> directories = new ArrayList<>();
    private final OnDirectoryClickListener clickListener;
    private final OnDirectoryActionListener actionListener;

    public interface OnDirectoryClickListener {
        void onDirectoryClick(TrackedDirectory directory);
    }

    public interface OnDirectoryActionListener {
        void onRescan(TrackedDirectory directory);
        void onDelete(TrackedDirectory directory);
    }

    public DirectoryAdapter(OnDirectoryClickListener clickListener,
                            OnDirectoryActionListener actionListener) {
        this.clickListener = clickListener;
        this.actionListener = actionListener;
    }

    public void setDirectories(List<TrackedDirectory> directories) {
        this.directories = directories != null ? directories : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_directory, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TrackedDirectory dir = directories.get(position);
        holder.bind(dir);
    }

    @Override
    public int getItemCount() {
        return directories.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {

        private final TextView textDirName;
        private final TextView textDirPath;
        private final TextView textFileCount;
        private final TextView textStatus;
        private final TextView textLastScanned;
        private final View buttonRescan;
        private final View buttonDelete;

        ViewHolder(View itemView) {
            super(itemView);
            textDirName = itemView.findViewById(R.id.text_dir_name);
            textDirPath = itemView.findViewById(R.id.text_dir_path);
            textFileCount = itemView.findViewById(R.id.text_file_count);
            textStatus = itemView.findViewById(R.id.text_status);
            textLastScanned = itemView.findViewById(R.id.text_last_scanned);
            buttonRescan = itemView.findViewById(R.id.button_rescan);
            buttonDelete = itemView.findViewById(R.id.button_delete);
        }

        void bind(TrackedDirectory dir) {
            textDirName.setText(dir.getDisplayName());
            textDirPath.setText(dir.getTreeUri());

            // File count
            if (dir.getTotalFiles() > 0) {
                textFileCount.setText(itemView.getContext().getString(
                        R.string.directory_indexed_count, dir.getIndexedFiles(), dir.getTotalFiles()));
            } else {
                textFileCount.setText(R.string.directory_never_scanned);
            }

            // Status
            DirectoryStatus status = dir.getStatus();
            String statusText;
            int statusColor;
            switch (status) {
                case SCANNING:
                    statusText = itemView.getContext().getString(R.string.label_scanning);
                    statusColor = itemView.getContext().getColor(R.color.status_scanning);
                    break;
                case INGESTING:
                    statusText = itemView.getContext().getString(R.string.label_ingesting);
                    statusColor = itemView.getContext().getColor(R.color.status_ingesting);
                    break;
                case ERROR:
                    statusText = itemView.getContext().getString(R.string.label_error);
                    statusColor = itemView.getContext().getColor(R.color.status_error);
                    break;
                default:
                    statusText = itemView.getContext().getString(R.string.label_idle);
                    statusColor = itemView.getContext().getColor(R.color.status_idle);
                    break;
            }
            textStatus.setText(statusText);
            textStatus.setTextColor(statusColor);

            // Last scanned
            if (dir.getLastScannedAt() > 0) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                textLastScanned.setText(itemView.getContext().getString(
                        R.string.directory_last_scanned, sdf.format(new Date(dir.getLastScannedAt()))));
            } else {
                textLastScanned.setText("");
            }

            // Click to view files
            itemView.setOnClickListener(v -> clickListener.onDirectoryClick(dir));

            // Action buttons
            buttonRescan.setOnClickListener(v -> actionListener.onRescan(dir));
            buttonDelete.setOnClickListener(v -> actionListener.onDelete(dir));
        }
    }
}
