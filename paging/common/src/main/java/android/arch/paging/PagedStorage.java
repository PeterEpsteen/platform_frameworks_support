/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.arch.paging;

import android.support.annotation.NonNull;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class PagedStorage<K, V> extends AbstractList<V> {
    // Always set
    private int mLeadingNullCount;
    /**
     * List of pages in storage.
     *
     * Two storage modes:
     *
     * Contiguous - all content in mPages is valid and loaded, but may return false from isTiled().
     *     Safe to access any item in any page.
     *
     * Non-contiguous - mPages may have nulls or a placeholder page, isTiled() always returns true.
     *     mPages may have nulls, or placeholder (empty) pages while content is loading.
     */
    private final ArrayList<Page<K, V>> mPages;
    private int mTrailingNullCount;

    private int mPositionOffset;
    /**
     * Number of items represented by {@link #mPages}. If tiling is enabled, unloaded items in
     * {@link #mPages} may be null, but this value still counts them.
     */
    private int mStorageCount;

    // If mPageSize > 0, tiling is enabled, 'mPages' may have gaps, and leadingPages is set
    private int mPageSize;

    private int mNumberPrepended;
    private int mNumberAppended;

    // only used in tiling case
    private Page<K, V> mPlaceholderPage;

    PagedStorage() {
        mLeadingNullCount = 0;
        mPages = new ArrayList<>();
        mTrailingNullCount = 0;
        mPositionOffset = 0;
        mStorageCount = 0;
        mPageSize = 1;
        mNumberPrepended = 0;
        mNumberAppended = 0;
    }

    PagedStorage(int leadingNulls, Page<K, V> page, int trailingNulls) {
        this();
        init(leadingNulls, page, trailingNulls, 0);
    }

    private PagedStorage(PagedStorage<K, V> other) {
        mLeadingNullCount = other.mLeadingNullCount;
        mPages = new ArrayList<>(other.mPages);
        mTrailingNullCount = other.mTrailingNullCount;
        mPositionOffset = other.mPositionOffset;
        mStorageCount = other.mStorageCount;
        mPageSize = other.mPageSize;
        mNumberPrepended = other.mNumberPrepended;
        mNumberAppended = other.mNumberAppended;

        // preserve placeholder page so we can locate placeholder pages if needed later
        mPlaceholderPage = other.mPlaceholderPage;
    }

    PagedStorage<K, V> snapshot() {
        return new PagedStorage<>(this);
    }

    private void init(int leadingNulls, Page<K, V> page, int trailingNulls, int positionOffset) {
        mLeadingNullCount = leadingNulls;
        mPages.clear();
        mPages.add(page);
        mTrailingNullCount = trailingNulls;

        mPositionOffset = positionOffset;
        mStorageCount = page.items.size();

        // initialized as tiled. There may be 3 nulls, 2 items, but we still call this tiled
        // even if it will break if nulls convert.
        mPageSize = page.items.size();

        mNumberPrepended = 0;
        mNumberAppended = 0;
    }

    void init(int leadingNulls, Page<K, V> page, int trailingNulls, int positionOffset,
            @NonNull Callback callback) {
        init(leadingNulls, page, trailingNulls, positionOffset);
        callback.onInitialized(size());
    }

    @Override
    public V get(int i) {
        if (i < 0 || i >= size()) {
            throw new IndexOutOfBoundsException("Index: " + i + ", Size: " + size());
        }

        // is it definitely outside 'mPages'?
        int localIndex = i - mLeadingNullCount;
        if (localIndex < 0 || localIndex >= mStorageCount) {
            return null;
        }

        int localPageIndex;
        int pageInternalIndex;

        if (isTiled()) {
            // it's inside mPages, and we're tiled. Jump to correct tile.
            localPageIndex = localIndex / mPageSize;
            pageInternalIndex = localIndex % mPageSize;
        } else {
            // it's inside mPages, but page sizes aren't regular. Walk to correct tile.
            // Pages can only be null while tiled, so accessing page count is safe.
            pageInternalIndex = localIndex;
            final int localPageCount = mPages.size();
            for (localPageIndex = 0; localPageIndex < localPageCount; localPageIndex++) {
                int pageSize = mPages.get(localPageIndex).items.size();
                if (pageSize > pageInternalIndex) {
                    // stop, found the page
                    break;
                }
                pageInternalIndex -= pageSize;
            }
        }

        Page<?, V> page = mPages.get(localPageIndex);
        if (page == null || page.items.size() == 0) {
            // can only occur in tiled case, with untouched inner/placeholder pages
            return null;
        }
        return page.items.get(pageInternalIndex);
    }

    /**
     * Returns true if all pages are the same size, except for the last, which may be smaller
     */
    boolean isTiled() {
        return mPageSize > 0;
    }

    int getLeadingNullCount() {
        return mLeadingNullCount;
    }

    int getTrailingNullCount() {
        return mTrailingNullCount;
    }

    int getStorageCount() {
        return mStorageCount;
    }

    int getNumberAppended() {
        return mNumberAppended;
    }

    int getNumberPrepended() {
        return mNumberPrepended;
    }

    int getPageCount() {
        return mPages.size();
    }

    interface Callback {
        void onInitialized(int count);
        void onPagePrepended(int leadingNulls, int changed, int added);
        void onPageAppended(int endPosition, int changed, int added);
        void onPagePlaceholderInserted(int pageIndex);
        void onPageInserted(int start, int count);
    }

    int getPositionOffset() {
        return mPositionOffset;
    }

    @Override
    public int size() {
        return mLeadingNullCount + mStorageCount + mTrailingNullCount;
    }

    int computeLeadingNulls() {
        int total = mLeadingNullCount;
        final int pageCount = mPages.size();
        for (int i = 0; i < pageCount; i++) {
            Page page = mPages.get(i);
            if (page != null && page != mPlaceholderPage) {
                break;
            }
            total += mPageSize;
        }
        return total;
    }

    int computeTrailingNulls() {
        int total = mTrailingNullCount;
        for (int i = mPages.size() - 1; i >= 0; i--) {
            Page page = mPages.get(i);
            if (page != null && page != mPlaceholderPage) {
                break;
            }
            total += mPageSize;
        }
        return total;
    }

    // ---------------- Contiguous API -------------------

    V getFirstContiguousItem() {
        // safe to access first page's first item here:
        // If contiguous, mPages can't be empty, can't hold null Pages, and items can't be empty
        return mPages.get(0).items.get(0);
    }

    V getLastContiguousItem() {
        // safe to access last page's last item here:
        // If contiguous, mPages can't be empty, can't hold null Pages, and items can't be empty
        Page<K, V> page = mPages.get(mPages.size() - 1);
        return page.items.get(page.items.size() - 1);
    }

    public void prependPage(@NonNull Page<K, V> page, @NonNull Callback callback) {
        final int count = page.items.size();
        if (count == 0) {
            // Nothing returned from source, stop loading in this direction
            return;
        }
        if (mPageSize > 0 && count != mPageSize) {
            if (mPages.size() == 1 && count > mPageSize) {
                // prepending to a single item - update current page size to that of 'inner' page
                mPageSize = count;
            } else {
                // no longer tiled
                mPageSize = -1;
            }
        }

        mPages.add(0, page);
        mStorageCount += count;

        final int changedCount = Math.min(mLeadingNullCount, count);
        final int addedCount = count - changedCount;

        if (changedCount != 0) {
            mLeadingNullCount -= changedCount;
        }
        mPositionOffset -= addedCount;
        mNumberPrepended += count;

        callback.onPagePrepended(mLeadingNullCount, changedCount, addedCount);
    }

    public void appendPage(@NonNull Page<K, V> page, @NonNull Callback callback) {
        final int count = page.items.size();
        if (count == 0) {
            // Nothing returned from source, stop loading in this direction
            return;
        }

        if (mPageSize > 0) {
            // if the previous page was smaller than mPageSize,
            // or if this page is larger than the previous, disable tiling
            if (mPages.get(mPages.size() - 1).items.size() != mPageSize
                    || count > mPageSize) {
                mPageSize = -1;
            }
        }

        mPages.add(page);
        mStorageCount += count;

        final int changedCount = Math.min(mTrailingNullCount, count);
        final int addedCount = count - changedCount;

        if (changedCount != 0) {
            mTrailingNullCount -= changedCount;
        }
        mNumberAppended += count;
        callback.onPageAppended(mLeadingNullCount + mStorageCount - count,
                changedCount, addedCount);
    }

    // ------------------ Non-Contiguous API (tiling required) ----------------------

    public void insertPage(int position, @NonNull Page<K, V> page, Callback callback) {
        final int newPageSize = page.items.size();
        if (newPageSize != mPageSize) {
            // differing page size is OK in 2 cases, when the page is being added:
            // 1) to the end (in which case, ignore new smaller size)
            // 2) only the last page has been added so far (in which case, adopt new bigger size)

            int size = size();
            boolean addingLastPage = position == (size - size % mPageSize)
                    && newPageSize < mPageSize;
            boolean onlyEndPagePresent = mTrailingNullCount == 0 && mPages.size() == 1
                    && newPageSize > mPageSize;

            // OK only if existing single page, and it's the last one
            if (!onlyEndPagePresent && !addingLastPage) {
                throw new IllegalArgumentException("page introduces incorrect tiling");
            }
            if (onlyEndPagePresent) {
                mPageSize = newPageSize;
            }
        }

        int pageIndex = position / mPageSize;

        allocatePageRange(pageIndex, pageIndex);

        int localPageIndex = pageIndex - mLeadingNullCount / mPageSize;

        Page<K, V> oldPage = mPages.get(localPageIndex);
        if (oldPage != null && oldPage != mPlaceholderPage) {
            throw new IllegalArgumentException(
                    "Invalid position " + position + ": data already loaded");
        }
        mPages.set(localPageIndex, page);
        callback.onPageInserted(position, page.items.size());
    }

    private Page<K, V> getPlaceholderPage() {
        if (mPlaceholderPage == null) {
            @SuppressWarnings("unchecked")
            List<V> list = Collections.emptyList();
            mPlaceholderPage = new Page<>(null, list, null);
        }
        return mPlaceholderPage;
    }

    private void allocatePageRange(final int minimumPage, final int maximumPage) {
        int leadingNullPages = mLeadingNullCount / mPageSize;

        if (minimumPage < leadingNullPages) {
            for (int i = 0; i < leadingNullPages - minimumPage; i++) {
                mPages.add(0, null);
            }
            int newStorageAllocated = (leadingNullPages - minimumPage) * mPageSize;
            mStorageCount += newStorageAllocated;
            mLeadingNullCount -= newStorageAllocated;

            leadingNullPages = minimumPage;
        }
        if (maximumPage >= leadingNullPages + mPages.size()) {
            int newStorageAllocated = Math.min(mTrailingNullCount,
                    (maximumPage + 1 - (leadingNullPages + mPages.size())) * mPageSize);
            for (int i = mPages.size(); i <= maximumPage - leadingNullPages; i++) {
                mPages.add(mPages.size(), null);
            }
            mStorageCount += newStorageAllocated;
            mTrailingNullCount -= newStorageAllocated;
        }
    }

    public void allocatePlaceholders(int index, int prefetchDistance,
            int pageSize, Callback callback) {
        if (pageSize != mPageSize) {
            if (pageSize < mPageSize) {
                throw new IllegalArgumentException("Page size cannot be reduced");
            }
            if (mPages.size() != 1 || mTrailingNullCount != 0) {
                // not in single, last page allocated case - can't change page size
                throw new IllegalArgumentException(
                        "Page size can change only if last page is only one present");
            }
            mPageSize = pageSize;
        }

        final int maxPageCount = (size() + mPageSize - 1) / mPageSize;
        int minimumPage = Math.max((index - prefetchDistance) / mPageSize, 0);
        int maximumPage = Math.min((index + prefetchDistance) / mPageSize, maxPageCount - 1);

        allocatePageRange(minimumPage, maximumPage);
        int leadingNullPages = mLeadingNullCount / mPageSize;
        for (int pageIndex = minimumPage; pageIndex <= maximumPage; pageIndex++) {
            int localPageIndex = pageIndex - leadingNullPages;
            if (mPages.get(localPageIndex) == null) {
                mPages.set(localPageIndex, getPlaceholderPage());
                callback.onPagePlaceholderInserted(pageIndex);
            }
        }
    }

    public boolean hasPage(int pageSize, int index) {
        // NOTE: we pass pageSize here to avoid in case mPageSize
        // not fully initialized (when last page only one loaded)
        int leadingNullPages = mLeadingNullCount / pageSize;

        if (index < leadingNullPages || index >= leadingNullPages + mPages.size()) {
            return false;
        }

        Page<K, V> page = mPages.get(index - leadingNullPages);

        return page != null && page != mPlaceholderPage;
    }

    @Override
    public String toString() {
        StringBuilder ret = new StringBuilder("leading " + mLeadingNullCount
                + ", storage " + mStorageCount
                + ", trailing " + getTrailingNullCount());

        for (int i = 0; i < mPages.size(); i++) {
            ret.append(" ").append(mPages.get(i));
        }
        return ret.toString();
    }
}
