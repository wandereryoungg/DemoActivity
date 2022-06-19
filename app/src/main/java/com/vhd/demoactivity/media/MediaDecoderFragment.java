package com.vhd.demoactivity.media;

import android.animation.TimeAnimator;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.vhd.demoactivity.R;

import java.io.IOException;

public class MediaDecoderFragment extends Fragment {

    private String TAG = getClass().getSimpleName();

    private Handler playHandler;
    private TextureView mPlaybackView;
    private TimeAnimator mTimeAnimator = new TimeAnimator();

    private MediaCodecWrapper mCodecWrapper;
    private MediaExtractor mExtractor = new MediaExtractor();

    static class SingleTon {
        private static final MediaDecoderFragment INSTANCE = new MediaDecoderFragment();
    }

    public static MediaDecoderFragment instance() {
        return SingleTon.INSTANCE;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_media_decoder, container, false);
        mPlaybackView = view.findViewById(R.id.play_back);
        mPlaybackView.postDelayed(new Runnable() {
            @Override
            public void run() {
                new PlayMovieThread().start();
            }
        }, 1000);
        return view;
    }

    @Override
    public void onStop() {
        super.onStop();
        playHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mTimeAnimator != null && mTimeAnimator.isRunning()) {
                    mTimeAnimator.end();
                }

                if (mCodecWrapper != null) {
                    mCodecWrapper.stopAndRelease();
                    mExtractor.release();
                }
            }
        });
    }

    private class PlayMovieThread extends Thread {

        @Override
        public void run() {
            super.run();
            Looper.prepare();
            playHandler = new Handler(Looper.myLooper());
            startPlayback();
            Looper.loop();
        }

    }

    public void startPlayback() {
        String path = "android.resource://" + getActivity().getPackageName() + "/" + R.raw.vid_bigbuckbunny;
        Log.e(TAG, "video source path: " + path);
        Uri videoUri = Uri.parse(path);

        try {
            // initialize_extractor
            mExtractor.setDataSource(getActivity(), videoUri, null);
            int nTracks = mExtractor.getTrackCount();
            Log.e(TAG, "nTracks: " + nTracks);
            // Begin by unselecting all of the tracks in the extractor, so we won't see any tracks that we haven't explicitly selected.
            for (int i = 0; i < nTracks; ++i) {
                mExtractor.unselectTrack(i);
            }

            for (int i = 0; i < nTracks; ++i) {
                mCodecWrapper = MediaCodecWrapper.fromVideoFormat(mExtractor.getTrackFormat(i), new Surface(mPlaybackView.getSurfaceTexture()));
                if (mCodecWrapper != null) {
                    Log.e(TAG, "select track: " + i);
                    mExtractor.selectTrack(i);
                    break;
                }
            }

            // By using a {@link TimeAnimator}, we can sync our media rendering commands with
            // the system display frame rendering. The animator ticks as the {@link Choreographer}
            // receives VSYNC events.
            mTimeAnimator.setTimeListener(new TimeAnimator.TimeListener() {
                @Override
                public void onTimeUpdate(final TimeAnimator animation, final long totalTime, final long deltaTime) {
                    Log.e(TAG, "onTimeUpdate:   deltaTime: " + deltaTime);
                    boolean isEos = ((mExtractor.getSampleFlags() & MediaCodec.BUFFER_FLAG_END_OF_STREAM) == MediaCodec.BUFFER_FLAG_END_OF_STREAM);

                    if (!isEos) {
                        // Try to submit the sample to the codec and if successful, advance the
                        // extractor to the next available sample to read.
                        boolean result = mCodecWrapper.writeSample(mExtractor, false,
                                mExtractor.getSampleTime(), mExtractor.getSampleFlags());
                        Log.e(TAG, "result: " + result);
                        if (result) {
                            Log.e(TAG, "current thread: " + Thread.currentThread().getName());
                            // Advancing the extractor is a blocking operation and it MUST be
                            // executed outside the main thread in real applications.
                            mExtractor.advance();
                        }
                    }
                    // END_INCLUDE(write_sample)

                    // Examine the sample at the head of the queue to see if its ready to be
                    // rendered and is not zero sized End-of-Stream record.
                    MediaCodec.BufferInfo out_bufferInfo = new MediaCodec.BufferInfo();
                    Log.e(TAG, "peekSample");
                    mCodecWrapper.peekSample(out_bufferInfo);

                    // BEGIN_INCLUDE(render_sample)
                    if (out_bufferInfo.size <= 0 && isEos) {
                        mTimeAnimator.end();
                        mCodecWrapper.stopAndRelease();
                        mExtractor.release();
                    } else if (out_bufferInfo.presentationTimeUs / 1000 < totalTime) {
                        // Pop the sample off the queue and send it to {@link Surface}
                        Log.e(TAG, "popSample");
                        mCodecWrapper.popSample(true);
                    }
                    // END_INCLUDE(render_sample)

                }
            });

            // We're all set. Kick off the animator to process buffers and render video frames as
            // they become available
            mTimeAnimator.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
