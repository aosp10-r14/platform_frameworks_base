/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.wm;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.proto.ProtoOutputStream;
import android.view.InsetsState;
import android.view.SurfaceControl;
import android.view.SurfaceControl.Transaction;
import android.view.InsetsSource;
import android.view.InsetsSourceControl;

import com.android.internal.util.function.TriConsumer;
import com.android.server.wm.SurfaceAnimator.OnAnimationFinishedCallback;

import java.io.PrintWriter;

/**
 * Controller for a specific inset source on the server. It's called provider as it provides the
 * {@link InsetsSource} to the client that uses it in {@link InsetsSourceConsumer}.
 */
class InsetsSourceProvider {

    private final Rect mTmpRect = new Rect();
    private final @NonNull InsetsSource mSource;
    private final DisplayContent mDisplayContent;
    private final InsetsStateController mStateController;
    private @Nullable InsetsSourceControl mControl;
    private @Nullable WindowState mControllingWin;
    private @Nullable ControlAdapter mAdapter;
    private WindowState mWin;
    private TriConsumer<DisplayFrames, WindowState, Rect> mFrameProvider;

    /** The visibility override from the current controlling window. */
    private boolean mClientVisible;

    /**
     * Whether the window is available and considered visible as in {@link WindowState#isVisible}.
     */
    private boolean mServerVisible;


    InsetsSourceProvider(InsetsSource source, InsetsStateController stateController,
            DisplayContent displayContent) {
        mClientVisible = InsetsState.getDefaultVisibly(source.getType());
        mSource = source;
        mDisplayContent = displayContent;
        mStateController = stateController;
    }

    InsetsSource getSource() {
        return mSource;
    }

    /**
     * Updates the window that currently backs this source.
     *
     * @param win The window that links to this source.
     * @param frameProvider Based on display frame state and the window, calculates the resulting
     *                      frame that should be reported to clients.
     */
    void setWindow(@Nullable WindowState win,
            @Nullable TriConsumer<DisplayFrames, WindowState, Rect> frameProvider) {
        if (mWin != null) {
            mWin.setInsetProvider(null);
        }
        mWin = win;
        mFrameProvider = frameProvider;
        if (win == null) {
            setServerVisible(false);
            mSource.setFrame(new Rect());
        } else {
            mWin.setInsetProvider(this);
        }
    }

    /**
     * Called when a layout pass has occurred.
     */
    void onPostLayout() {
        if (mWin == null) {
            return;
        }

        mTmpRect.set(mWin.getFrameLw());
        if (mFrameProvider != null) {
            mFrameProvider.accept(mWin.getDisplayContent().mDisplayFrames, mWin, mTmpRect);
        } else {
            mTmpRect.inset(mWin.mGivenContentInsets);
        }
        mSource.setFrame(mTmpRect);
        setServerVisible(mWin.isVisible() && !mWin.mGivenInsetsPending);

    }

    void updateControlForTarget(@Nullable WindowState target) {
        if (target == mControllingWin) {
            return;
        }
        if (target == null) {
            revokeControl();
            return;
        }
        mAdapter = new ControlAdapter();
        mWin.startAnimation(mDisplayContent.getPendingTransaction(), mAdapter,
                false /* TODO hidden */);
        mControllingWin = target;
        mControl = new InsetsSourceControl(mSource.getType(), mAdapter.mCapturedLeash);
        setClientVisible(InsetsState.getDefaultVisibly(mSource.getType()));
    }

    boolean onInsetsModified(WindowState caller, InsetsSource modifiedSource) {
        if (mControllingWin != caller || modifiedSource.isVisible() == mClientVisible) {
            return false;
        }
        setClientVisible(modifiedSource.isVisible());
        return true;
    }

    private void setClientVisible(boolean clientVisible) {
        mClientVisible = clientVisible;
        updateVisibility();
    }

    private void setServerVisible(boolean serverVisible) {
        mServerVisible = serverVisible;
        updateVisibility();
    }

    private void updateVisibility() {
        mSource.setVisible(mServerVisible && mClientVisible);
    }

    InsetsSourceControl getControl() {
        return mControl;
    }

    void revokeControl() {
        if (mControllingWin != null) {

            // Cancelling the animation will invoke onAnimationCancelled, resetting all the fields.
            mWin.cancelAnimation();
        }
        setClientVisible(InsetsState.getDefaultVisibly(mSource.getType()));
    }

    private class ControlAdapter implements AnimationAdapter {

        private SurfaceControl mCapturedLeash;

        @Override
        public boolean getShowWallpaper() {
            return false;
        }

        @Override
        public int getBackgroundColor() {
            return 0;
        }

        @Override
        public void startAnimation(SurfaceControl animationLeash, Transaction t,
                OnAnimationFinishedCallback finishCallback) {
            mCapturedLeash = animationLeash;
            t.setPosition(mCapturedLeash, mSource.getFrame().left, mSource.getFrame().top);
        }

        @Override
        public void onAnimationCancelled(SurfaceControl animationLeash) {
            if (mAdapter == this) {
                mStateController.notifyControlRevoked(mControllingWin, InsetsSourceProvider.this);
                mControl = null;
                mControllingWin = null;
                mAdapter = null;
            }
        }

        @Override
        public long getDurationHint() {
            return 0;
        }

        @Override
        public long getStatusBarTransitionsStartTime() {
            return 0;
        }

        @Override
        public void dump(PrintWriter pw, String prefix) {
        }

        @Override
        public void writeToProto(ProtoOutputStream proto) {
        }
    };
}
