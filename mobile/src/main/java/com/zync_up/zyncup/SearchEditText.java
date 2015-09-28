package com.zync_up.zyncup;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.QwertyKeyListener;
import android.text.style.ImageSpan;
import android.text.util.Rfc822Token;
import android.text.util.Rfc822Tokenizer;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Filterable;
import android.widget.ListAdapter;
import android.widget.MultiAutoCompleteTextView;
import android.widget.TextView;

import com.zync_up.zyncup.recipientchip.DrawableRecipientChip;
import com.zync_up.zyncup.recipientchip.VisibleRecipientChip;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SearchEditText extends MultiAutoCompleteTextView
        implements GestureDetector.OnGestureListener, TextView.OnEditorActionListener,
        OnItemClickListener {

    private static final char COMMIT_CHAR_COMMA = ',';

    private static final char COMMIT_CHAR_SEMICOLON = ';';

    private static final char COMMIT_CHAR_SPACE = ' ';

    private static final String SEPARATOR = String.valueOf(COMMIT_CHAR_COMMA)
            + String.valueOf(COMMIT_CHAR_SPACE);

    private static final String TAG = "SearchEditText";

    // Resources for displaying chips.
    private Drawable mChipBackground = null;

    private Drawable mChipDelete = null;

    private Drawable mChipBackgroundPressed;

    private float mChipHeight;

    private float mChipFontSize;

    private float mLineSpacingExtra;

    private int mChipPadding;

    private DropdownLayoutAdapter mDropdownLayoutAdapter;

    private Context mContext;

    // This pattern comes from android.util.Patterns. It has been tweaked to handle a "1" before
    // parens, so numbers such as "1 (425) 222-2342" match.
    private static final Pattern PHONE_PATTERN
            = Pattern.compile(                                  // sdd = space, dot, or dash
            "(\\+[0-9]+[\\- \\.]*)?"                    // +<digits><sdd>*
                    + "(1?[ ]*\\([0-9]+\\)[\\- \\.]*)?"         // 1(<digits>)<sdd>*
                    + "([0-9][0-9\\- \\.][0-9\\- \\.]+[0-9])"); // <digit><digit|sdd>+<digit>

    /**
     * Enumerator for avatar position. See attr.xml for more details.
     * 0 for end, 1 for start.
     */
    private int mAvatarPosition;

    private static final int AVATAR_POSITION_END = 0;

    /**
     * Enumerator for image span alignment. See attr.xml for more details.
     * 0 for bottom, 1 for baseline.
     */
    private int mImageSpanAlignment;

    private static final int IMAGE_SPAN_ALIGNMENT_BOTTOM = 0;

    private static final int IMAGE_SPAN_ALIGNMENT_BASELINE = 1;

    private boolean mDisableDelete;

    private Tokenizer mTokenizer;

    private DrawableRecipientChip mSelectedChip;

    // VisibleForTesting
    final ArrayList<String> mPendingChips = new ArrayList<String>();

    private int mPendingChipsCount = 0;

    private boolean mNoChips = false;

    // VisibleForTesting
    ArrayList<DrawableRecipientChip> mTemporaryRecipients;

    private boolean mShouldShrink = true;

    // Chip copy fields.
    private GestureDetector mGestureDetector;

    private TextWatcher mTextWatcher;

    private static int sExcessTopPadding = -1;

    private int mActionBarHeight;

    private final Runnable mAddTextWatcher = new Runnable() {
        @Override
        public void run() {
            if (mTextWatcher == null) {
                mTextWatcher = new RecipientTextWatcher();
                addTextChangedListener(mTextWatcher);
            }
        }
    };

    /**
     * PUBLIC CONSTRUCTOR
     * PUBLIC CONSTRUCTOR
     * PUBLIC CONSTRUCTOR
     */
    public SearchEditText(Context context, AttributeSet attrs) {
        super(context, attrs);

        setChipDimensions(context, attrs);

        mContext = context;

        setInputType(getInputType() | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);

        setOnEditorActionListener(this);

        mGestureDetector = new GestureDetector(context, this);

        mTextWatcher = new RecipientTextWatcher();
        addTextChangedListener(mTextWatcher);

        setDropdownChipLayouter(new DropdownLayoutAdapter(LayoutInflater.from(context), context));

        setOnItemClickListener(this);
    }

    @Override
    public boolean onDown(MotionEvent e) {
        return false;
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        // Do nothing.
        return false;
    }

    @Override
    public boolean onEditorAction(TextView view, int action, KeyEvent keyEvent) {
        if (action == EditorInfo.IME_ACTION_DONE) {
            if (mSelectedChip != null) {
                clearSelectedChip();
                return true;
            } else if (focusNext()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Dismiss any selected chips when the back key is pressed.
     */
    @Override
    public boolean onKeyPreIme(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && mSelectedChip != null) {
            clearSelectedChip();
            return true;
        }
        return super.onKeyPreIme(keyCode, event);
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        // Do nothing.
        return false;
    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        // Do nothing.
        return false;
    }

    /**
     * Monitor touch events in the RecipientEditTextView.
     * If the view does not have focus, any tap on the view
     * will just focus the view. If the view has focus, determine
     * if the touch target is a recipient chip. If it is and the chip
     * is not selected, select it and clear any other selected chips.
     * If it isn't, then select that chip.
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isFocused()) {
            // Ignore any chip taps until this view is focused.
            return super.onTouchEvent(event);
        }
        boolean handled = super.onTouchEvent(event);
        int action = event.getAction();
        boolean chipWasSelected = false;
        if (mSelectedChip == null) {
            mGestureDetector.onTouchEvent(event);
        }
        if (action == MotionEvent.ACTION_UP) {
            float x = event.getX();
            float y = event.getY();
            int offset = putOffsetInRange(x, y);
            DrawableRecipientChip currentChip = findChip(offset);
            if (currentChip != null) {
                if (action == MotionEvent.ACTION_UP) {
                    if (mSelectedChip != null && mSelectedChip != currentChip) {
                        clearSelectedChip();
                        mSelectedChip = selectChip(currentChip);
                    } else if (mSelectedChip == null) {
                        setSelection(getText().length());
                        mSelectedChip = selectChip(currentChip);
                    } else {
                        onClick(mSelectedChip, offset, x, y);
                    }
                }
                chipWasSelected = true;
                handled = true;
            }
        }
        if (action == MotionEvent.ACTION_UP && !chipWasSelected) {
            clearSelectedChip();
        }
        return handled;
    }

    /**
     * Convenience method: Append the specified text slice to the TextView's
     * display buffer, upgrading it to BufferType.EDITABLE if it was
     * not already editable. Commas are excluded as they are added automatically
     * by the view.
     */
    @Override
    public void append(CharSequence text, int start, int end) {
        // We don't care about watching text changes while appending.
        if (mTextWatcher != null) {
            removeTextChangedListener(mTextWatcher);
        }
        super.append(text, start, end);
        if (!TextUtils.isEmpty(text) && TextUtils.getTrimmedLength(text) > 0) {
            String displayString = text.toString();

            if (!displayString.trim().endsWith(String.valueOf(COMMIT_CHAR_COMMA))) {
                // We have no separator, so we should add it
                super.append(SEPARATOR, 0, SEPARATOR.length());
                displayString += SEPARATOR;
            }

            if (!TextUtils.isEmpty(displayString)
                    && TextUtils.getTrimmedLength(displayString) > 0) {
                mPendingChipsCount++;
                mPendingChips.add(displayString);
            }
        }
    }

    @Override
    public void onFocusChanged(boolean hasFocus, int direction, Rect previous) {
        super.onFocusChanged(hasFocus, direction, previous);
        if (!hasFocus) {
            //shrink();
        } else {
            expand();
        }
    }

    @Override
    public void onLongPress(MotionEvent event) {
        //Do nothing.
    }

    @Override
    public void onShowPress(MotionEvent e) {
        // Do nothing.
    }

    /**
     * We cannot use the default mechanism for replaceText. Instead,
     * we override onItemClickListener so we can get all the associated
     * contact information including display text, address, and id.
     */
    @Override
    protected void replaceText(CharSequence text) {
        return;
    }

    @Override
    public void setTokenizer(Tokenizer tokenizer) {
        mTokenizer = tokenizer;
        super.setTokenizer(mTokenizer);
    }


    /**
     * When an item in the suggestions list has been clicked, create a chip from the
     * contact information of the selected item.
     */
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (position < 0) {
            return;
        }
        submitItemAtPosition(position);
    }

    @Override
    public void onSelectionChanged(int start, int end) {
        // When selection changes, see if it is inside the chips area.
        // If so, move the cursor back after the chips again.
        DrawableRecipientChip last = getLastChip();
        if (last != null && start < getSpannable().getSpanEnd(last)) {
            // Grab the last chip and set the cursor to after it.
            setSelection(Math.min(getSpannable().getSpanEnd(last) + 1, getText().length()));
        }
        super.onSelectionChanged(start, end);
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        if (!TextUtils.isEmpty(getText())) {
            super.onRestoreInstanceState(null);
        } else {
            super.onRestoreInstanceState(state);
        }
    }

    @Override
    protected void performFiltering(CharSequence text, int keyCode) {
        if (TextUtils.isEmpty(text)) {
            getFilter().filter("", this);
            return;
        }
        boolean isCompletedToken = isCompletedToken(text);
        if (enoughToFilter() && !isCompletedToken) {
            int end = getSelectionEnd();
            int start = mTokenizer.findTokenStart(text, end);
            // If this is a RecipientChip, don't filter
            // on its contents.
            Spannable span = getSpannable();
            DrawableRecipientChip[] chips = span.getSpans(start, end, DrawableRecipientChip.class);
            if (chips != null && chips.length > 0) {
                dismissDropDown();
                return;
            }
        } else if (isCompletedToken) {
            dismissDropDown();
            return;
        }
        super.performFiltering(text, keyCode);
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        InputConnection connection = super.onCreateInputConnection(outAttrs);
        int imeActions = outAttrs.imeOptions&EditorInfo.IME_MASK_ACTION;
        if ((imeActions&EditorInfo.IME_ACTION_DONE) != 0) {
            // clear the existing action
            outAttrs.imeOptions ^= imeActions;
            // set the DONE action
            outAttrs.imeOptions |= EditorInfo.IME_ACTION_DONE;
        }
        if ((outAttrs.imeOptions&EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) {
            outAttrs.imeOptions &= ~EditorInfo.IME_FLAG_NO_ENTER_ACTION;
        }

        outAttrs.actionId = EditorInfo.IME_ACTION_DONE;
        outAttrs.actionLabel = getContext().getString(R.string.done);
        return connection;
    }

    @Override
    public Parcelable onSaveInstanceState() {
        // If the user changes orientation while they are editing, just roll back the selection.
        clearSelectedChip();
        return super.onSaveInstanceState();
    }

    @Override
    public <T extends ListAdapter & Filterable> void setAdapter(T adapter) {
        super.setAdapter(adapter);
        SearchAdapter searchAdapter = (SearchAdapter) adapter;
        searchAdapter.registerUpdateObserver(new SearchAdapter.ResultsUpdatedObserver() {
            @Override
            public void onChanged(List<SearchResult> entries) {

            }
        });
        searchAdapter.setDropdownChipLayouter(mDropdownLayoutAdapter);
    }

    private boolean alreadyHasChip(int start, int end) {
        if (mNoChips) {
            return true;
        }
        DrawableRecipientChip[] chips =
                getSpannable().getSpans(start, end, DrawableRecipientChip.class);
        if ((chips == null || chips.length == 0)) {
            return false;
        }
        return true;
    }

    private boolean commitChip(int start, int end, Editable editable) {
        ListAdapter adapter = getAdapter();
        if (adapter != null && adapter.getCount() > 0 && enoughToFilter()
                && end == getSelectionEnd()) {
            // choose the first entry.
            submitItemAtPosition(0);
            dismissDropDown();
            return true;
        } else {
            int tokenEnd = mTokenizer.findTokenEnd(editable, start);
            if (editable.length() > tokenEnd + 1) {
                char charAt = editable.charAt(tokenEnd + 1);
                if (charAt == COMMIT_CHAR_COMMA || charAt == COMMIT_CHAR_SEMICOLON) {
                    tokenEnd++;
                }
            }
            String text = editable.toString().substring(start, tokenEnd).trim();
            clearComposingText();
            if (text != null && text.length() > 0 && !text.equals(" ")) {
                SearchResult searchResult = createTokenizedEntry(text);
                if (searchResult != null) {
                    QwertyKeyListener.markAsReplaced(editable, start, end, "");
                    CharSequence chipText = createChip(searchResult, false);
                    if (chipText != null && start > -1 && end > -1) {
                        editable.replace(start, end, chipText);
                    }
                }
                // Only dismiss the dropdown if it is related to the text we
                // just committed.
                // For paste, it may not be as there are possibly multiple
                // tokens being added.
                if (end == getSelectionEnd()) {
                    dismissDropDown();
                }
                sanitizeBetween();
                return true;
            }
        }
        return false;
    }

    private boolean focusNext() {
        View next = focusSearch(View.FOCUS_DOWN);
        if (next != null) {
            next.requestFocus();
            return true;
        }
        return false;
    }

    /**
     * Return whether a touch event was inside the delete target of
     * a selected chip. It is in the delete target if:
     * 1) the x and y points of the event are within the
     * delete assset.
     * 2) the point tapped would have caused a cursor to appear
     * right after the selected chip.
     * @return boolean
     */
    private boolean isInDelete(DrawableRecipientChip chip, int offset, float x, float y) {
        // Figure out the bounds of this chip and whether or not
        // the user clicked in the X portion.
        // TODO: Should x and y be used, or removed?
        if (mDisableDelete) {
            return false;
        }

        return chip.isSelected() &&
                ((mAvatarPosition == AVATAR_POSITION_END && offset == getChipEnd(chip)) ||
                        (mAvatarPosition != AVATAR_POSITION_END && offset == getChipStart(chip)));
    }

    boolean isCompletedToken(CharSequence text) {
        if (TextUtils.isEmpty(text)) {
            return false;
        }
        // Check to see if this is a completed token before filtering.
        int end = text.length();
        int start = mTokenizer.findTokenStart(text, end);
        String token = text.toString().substring(start, end).trim();
        if (!TextUtils.isEmpty(token)) {
            char atEnd = token.charAt(token.length() - 1);
            return atEnd == COMMIT_CHAR_COMMA || atEnd == COMMIT_CHAR_SEMICOLON;
        }
        return false;
    }

    private static boolean isPhoneNumber(String number) {
        // TODO: replace this function with libphonenumber's isPossibleNumber (see
        // PhoneNumberUtil). One complication is that it requires the sender's region which
        // comes from the CurrentCountryIso. For now, let's just do this simple match.
        if (TextUtils.isEmpty(number)) {
            return false;
        }

        Matcher match = PHONE_PATTERN.matcher(number);
        return match.matches();
    }

    public boolean lastCharacterIsCommitCharacter(CharSequence s) {
        char last;
        int end = getSelectionEnd() == 0 ? 0 : getSelectionEnd() - 1;
        int len = length() - 1;
        if (end != len) {
            last = s.charAt(end);
        } else {
            last = s.charAt(len);
        }
        return last == COMMIT_CHAR_COMMA || last == COMMIT_CHAR_SEMICOLON || last == COMMIT_CHAR_SPACE;
    }

    /**
     * Returns true if the avatar should be positioned at the right edge of the chip.
     * Takes into account both the set avatar position (start or end) as well as whether
     * the layout direction is LTR or RTL.
     */
    private boolean shouldPositionAvatarOnRight() {
        final boolean isRtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        final boolean assignedPosition = mAvatarPosition == AVATAR_POSITION_END;
        // If in Rtl mode, the position should be flipped.
        return isRtl ? !assignedPosition : assignedPosition;
    }

    /**
     * Get the max amount of space a chip can take up. The formula takes into
     * account the width of the EditTextView, any view padding, and padding
     * that will be added to the chip.
     */
    private float calculateAvailableWidth() {
        return getWidth() - getPaddingLeft() - getPaddingRight() - (mChipPadding * 2);
    }

    private static int findText(Editable text, int offset) {
        if (text.charAt(offset) != ' ') {
            return offset;
        }
        return -1;
    }

    /**
     * Given a height, returns a Y offset that will draw the text in the middle of the height.
     */
    protected float getTextYOffset(String text, TextPaint paint, int height) {
        Rect bounds = new Rect();
        paint.getTextBounds(text, 0, text.length(), bounds);
        int textHeight = bounds.bottom - bounds.top ;
        return height - ((height - textHeight) / 2) - (int)paint.descent()/2;
    }

    private int getChipStart(DrawableRecipientChip chip) {
        return getSpannable().getSpanStart(chip);
    }

    private int getChipEnd(DrawableRecipientChip chip) {
        return getSpannable().getSpanEnd(chip);
    }

    private int getExcessTopPadding() {
        if (sExcessTopPadding == -1) {
            sExcessTopPadding = (int) (mChipHeight + mLineSpacingExtra);
        }
        return sExcessTopPadding;
    }

    private int getImageSpanAlignment() {
        switch (mImageSpanAlignment) {
            case IMAGE_SPAN_ALIGNMENT_BASELINE:
                return ImageSpan.ALIGN_BASELINE;
            case IMAGE_SPAN_ALIGNMENT_BOTTOM:
                return ImageSpan.ALIGN_BOTTOM;
            default:
                return ImageSpan.ALIGN_BOTTOM;
        }
    }

    private int putOffsetInRange(final float x, final float y) {

        return putOffsetInRange(getOffsetForPosition(x,y));
    }

    // TODO: This algorithm will need a lot of tweaking after more people have used
    // the chips ui. This attempts to be "forgiving" to fat finger touches by favoring
    // what comes before the finger.
    private int putOffsetInRange(int o) {
        int offset = o;
        Editable text = getText();
        int length = text.length();
        // Remove whitespace from end to find "real end"
        int realLength = length;
        for (int i = length - 1; i >= 0; i--) {
            if (text.charAt(i) == ' ') {
                realLength--;
            } else {
                break;
            }
        }

        // If the offset is beyond or at the end of the text,
        // leave it alone.
        if (offset >= realLength) {
            return offset;
        }
        Editable editable = getText();
        while (offset >= 0 && findText(editable, offset) == -1 && findChip(offset) == null) {
            // Keep walking backward!
            offset--;
        }
        return offset;
    }

    private void clearSelectedChip() {
        if (mSelectedChip != null) {
            unselectChip(mSelectedChip);
            mSelectedChip = null;
        }
        setCursorVisible(true);
    }

    private void commitByCharacter() {
        // We can't possibly commit by character if we can't tokenize.
        if (mTokenizer == null) {
            return;
        }
        Editable editable = getText();
        int end = getSelectionEnd();
        int start = mTokenizer.findTokenStart(editable, end);
        commitChip(start, end, editable);
        setSelection(getText().length());
    }

    /**
     * Draws the icon onto the canvas given the source rectangle of the bitmap and the destination
     * rectangle of the canvas.
     */
    protected void drawIconOnCanvas(Bitmap icon, Canvas canvas, Paint paint, RectF src, RectF dst) {
        Matrix matrix = new Matrix();
        matrix.setRectToRect(src, dst, Matrix.ScaleToFit.FILL);
        canvas.drawBitmap(icon, matrix, paint);
    }

    private void expand() {
        if (mShouldShrink) {
            setMaxLines(Integer.MAX_VALUE);
        }
        setCursorVisible(true);
        Editable text = getText();
        setSelection(text != null && text.length() > 0 ? text.length() : 0);
        // If there are any temporary chips, try replacing them now that the user
        // has expanded the field.
        if (mTemporaryRecipients != null && mTemporaryRecipients.size() > 0) {
            mTemporaryRecipients = null;
        }
    }

    /**
     * Handle click events for a chip. When a selected chip receives a click
     * event, see if that event was in the delete icon. If so, delete it.
     * Otherwise, unselect the chip.
     */
    public void onClick(DrawableRecipientChip chip, int offset, float x, float y) {
        if (chip.isSelected()) {
            if (isInDelete(chip, offset, x, y)) {
                removeChip(chip);
            } else {
                clearSelectedChip();
            }
        }
    }

    /**
     * Remove the chip and any text associated with it from the RecipientEditTextView.
     */
    private void removeChip(DrawableRecipientChip chip) {
        Spannable spannable = getSpannable();
        int spanStart = spannable.getSpanStart(chip);
        int spanEnd = spannable.getSpanEnd(chip);
        Editable text = getText();
        int toDelete = spanEnd;
        boolean wasSelected = chip == mSelectedChip;
        // Clear that there is a selected chip before updating any text.
        if (wasSelected) {
            mSelectedChip = null;
        }
        // Always remove trailing spaces when removing a chip.
        while (toDelete >= 0 && toDelete < text.length() && text.charAt(toDelete) == ' ') {
            toDelete++;
        }
        spannable.removeSpan(chip);
        if (spanStart >= 0 && toDelete > 0) {
            text.delete(spanStart, toDelete);
        }
        if (wasSelected) {
            clearSelectedChip();
        }
    }

    private void sanitizeBetween() {
        // Don't sanitize while we are waiting for content to chipify.
        if (mPendingChipsCount > 0) {
            return;
        }
        // Find the last chip.
        DrawableRecipientChip[] recips = getSortedRecipients();
        if (recips != null && recips.length > 0) {
            DrawableRecipientChip last = recips[recips.length - 1];
            DrawableRecipientChip beforeLast = null;
            if (recips.length > 1) {
                beforeLast = recips[recips.length - 2];
            }
            int startLooking = 0;
            int end = getSpannable().getSpanStart(last);
            if (beforeLast != null) {
                startLooking = getSpannable().getSpanEnd(beforeLast);
                Editable text = getText();
                if (startLooking == -1 || startLooking > text.length() - 1) {
                    // There is nothing after this chip.
                    return;
                }
                if (text.charAt(startLooking) == ' ') {
                    startLooking++;
                }
            }
            if (startLooking >= 0 && end >= 0 && startLooking < end) {
                getText().delete(startLooking, end);
            }
        }
    }

    private void setChipDimensions(Context context, AttributeSet attrs) {
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.RecipientEditTextView, 0,
                0);
        Resources r = getContext().getResources();

        mChipBackground = a.getDrawable(R.styleable.RecipientEditTextView_chipBackground);
        if (mChipBackground == null) {
            mChipBackground = r.getDrawable(R.drawable.chip_background);
        }
        mChipBackgroundPressed = a
                .getDrawable(R.styleable.RecipientEditTextView_chipBackgroundPressed);
        if (mChipBackgroundPressed == null) {
            mChipBackgroundPressed = r.getDrawable(R.drawable.chip_background_selected);
        }
        mChipDelete = a.getDrawable(R.styleable.RecipientEditTextView_chipDelete);
        if (mChipDelete == null) {
            mChipDelete = r.getDrawable(R.drawable.chip_delete);
        }
        mChipPadding = a.getDimensionPixelSize(R.styleable.RecipientEditTextView_chipPadding, -1);
        if (mChipPadding == -1) {
            mChipPadding = (int) r.getDimension(R.dimen.chip_padding);
        }

        mChipHeight = a.getDimensionPixelSize(R.styleable.RecipientEditTextView_chipHeight, -1);
        if (mChipHeight == -1) {
            mChipHeight = r.getDimension(R.dimen.chip_height);
        }
        mChipFontSize = a.getDimensionPixelSize(R.styleable.RecipientEditTextView_chipFontSize, -1);
        if (mChipFontSize == -1) {
            mChipFontSize = r.getDimension(R.dimen.chip_text_size);
        }
        Drawable mInvalidChipBackground = a
                .getDrawable(R.styleable.RecipientEditTextView_invalidChipBackground);
        if (mInvalidChipBackground == null) {
            mInvalidChipBackground = r.getDrawable(R.drawable.chip_background_invalid);
        }
        mAvatarPosition = a.getInt(R.styleable.RecipientEditTextView_avatarPosition, 1);
        mImageSpanAlignment = a.getInt(R.styleable.RecipientEditTextView_imageSpanAlignment, 0);
        mDisableDelete = a.getBoolean(R.styleable.RecipientEditTextView_disableDelete, false);

        mLineSpacingExtra =  r.getDimension(R.dimen.line_spacing_extra);
        TypedValue tv = new TypedValue();
        if (context.getTheme().resolveAttribute(android.R.attr.actionBarSize, tv, true)) {
            mActionBarHeight = TypedValue.complexToDimensionPixelSize(tv.data, getResources()
                    .getDisplayMetrics());
        }

        a.recycle();
    }

    protected void setDropdownChipLayouter(DropdownLayoutAdapter dropdownLayoutAdapter) {
        mDropdownLayoutAdapter = dropdownLayoutAdapter;
    }

    /*private void shrink() {
        if (mTokenizer == null) {
            return;
        }
        if (getWidth() <= 0) {
            return;
        }
        Editable editable = getText();
        int end = getSelectionEnd();
        int start = mTokenizer.findTokenStart(editable, end);
        DrawableRecipientChip[] chips =
                getSpannable().getSpans(start, end, DrawableRecipientChip.class);
        if ((chips == null || chips.length == 0)) {
            Editable text = getText();
            int whatEnd = mTokenizer.findTokenEnd(text, start);
            // This token was already tokenized, so skip past the ending token.
            if (whatEnd < text.length() && text.charAt(whatEnd) == ',') {
                whatEnd = movePastTerminators(whatEnd);
            }
            // In the middle of chip; treat this as an edit
            // and commit the whole token.
            int selEnd = getSelectionEnd();
            if (whatEnd != selEnd) {
                handleEdit(start, whatEnd);
            } else {
                commitChip(start, end, editable);
            }
        }

        mHandler.post(mAddTextWatcher);
    }*/

    public void submitItem(SearchResult searchResult) {
        if (searchResult == null) {
            return;
        }
        clearComposingText();

        int end = getSelectionEnd();
        int start = mTokenizer.findTokenStart(getText(), end);

        Editable editable = getText();
        QwertyKeyListener.markAsReplaced(editable, start, end, "");
        CharSequence chip = createChip(searchResult, false);
        if (chip != null && start >= 0 && end >= 0) {
            editable.replace(start, end, chip);
        }
        sanitizeBetween();
    }

    private void submitItemAtPosition(int position) {
        SearchResult searchResult = (SearchResult) getAdapter().getItem(position);
        submitItem(searchResult);
    }

    /**
     * Remove selection from this chip. Unselecting a RecipientChip will render
     * the chip without a delete icon and with an unfocused background. This is
     * called when the RecipientChip no longer has focus.
     */
    private void unselectChip(DrawableRecipientChip chip) {
        int start = getChipStart(chip);
        int end = getChipEnd(chip);
        Editable editable = getText();
        mSelectedChip = null;

        getSpannable().removeSpan(chip);
        QwertyKeyListener.markAsReplaced(editable, start, end, "");
        editable.removeSpan(chip);

        try {
            if (!mNoChips) {
                editable.setSpan(constructChipSpan(chip.getSearchResult(), false),
                        start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        } catch (NullPointerException e) {
            Log.e(TAG, e.getMessage(), e);
        }

        setCursorVisible(true);
        setSelection(editable.length());
    }

    private Bitmap createChipBitmap(SearchResult searchResult, TextPaint paint, Bitmap icon,
                                    Drawable background) {
        if (background == null) {
            Log.w(TAG, "Unable to draw a background for the chips as it was never set");
            return Bitmap.createBitmap(
                    (int) mChipHeight * 2, (int) mChipHeight, Bitmap.Config.ARGB_8888);
        }

        Rect backgroundPadding = new Rect();
        background.getPadding(backgroundPadding);

        // Ellipsize the text so that it takes AT MOST the entire width of the
        // autocomplete text entry area. Make sure to leave space for padding
        // on the sides.
        int height = (int) mChipHeight + getResources().getDimensionPixelSize(R.dimen.extra_chip_height);
        // Since the icon is a square, it's width is equal to the maximum height it can be inside
        // the chip.
        int iconWidth = height - backgroundPadding.top - backgroundPadding.bottom;
        float[] widths = new float[1];
        paint.getTextWidths(" ", widths);
        CharSequence ellipsizedText = ellipsizeText(createChipDisplayText(searchResult), paint,
                calculateAvailableWidth() - iconWidth - widths[0] - backgroundPadding.left
                        - backgroundPadding.right);
        int textWidth = (int) paint.measureText(ellipsizedText, 0, ellipsizedText.length());

        // Make sure there is a minimum chip width so the user can ALWAYS
        // tap a chip without difficulty.
        int width = Math.max(iconWidth * 2, textWidth + (mChipPadding * 2) + iconWidth
                + backgroundPadding.left + backgroundPadding.right);

        // Create the background of the chip.
        Bitmap tmpBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(tmpBitmap);

        // Draw the background drawable
        background.setBounds(height/2, 0, width, height);
        background.draw(canvas);
        // Draw the text vertically aligned
        int textX = shouldPositionAvatarOnRight() ?
                mChipPadding + backgroundPadding.left :
                width - backgroundPadding.right - mChipPadding - textWidth;
        paint.setColor(0xFF5C5C5C);
        paint.setAntiAlias(true);
        canvas.drawText(ellipsizedText, 0, ellipsizedText.length(),
                textX, getTextYOffset(ellipsizedText.toString(), paint, height), paint);
        if (icon != null) {
            // Draw the icon
            icon = getClip(icon);
            int iconX = shouldPositionAvatarOnRight() ?
                    width - backgroundPadding.right - iconWidth :
                    backgroundPadding.left;
            RectF src = new RectF(0, 0, icon.getWidth(), icon.getHeight());
            RectF dst = new RectF(0, 0, height, height);
            drawIconOnCanvas(icon, canvas, paint, src, dst);
        }
        return tmpBitmap;
    }

    /**
     * Creates a bitmap of the given contact on a selected chip.
     *
     * @param searchResult The recipient entry to pull data from.
     * @param paint The paint to use to draw the bitmap.
     */
    private Bitmap createSelectedChip(SearchResult searchResult, TextPaint paint) {
        int sSelectedTextColor = -1;
        paint.setColor(sSelectedTextColor);
        Bitmap photo;
        if (mDisableDelete) {
            // Show the avatar instead if we don't want to delete
            photo = getAvatarIcon(searchResult);
        } else {
            photo = ((BitmapDrawable) mChipDelete).getBitmap();
        }
        return createChipBitmap(searchResult, paint, photo, mChipBackgroundPressed);
    }

    /**
     * Creates a bitmap of the given contact on a selected chip.
     *
     * @param searchResult The recipient entry to pull data from.
     * @param paint The paint to use to draw the bitmap.
     */
    private Bitmap createUnselectedChip(SearchResult searchResult, TextPaint paint) {
        Drawable background = mChipBackground;
        Bitmap photo = getAvatarIcon(searchResult);
        paint.setColor(getContext().getResources().getColor(android.R.color.black));
        return createChipBitmap(searchResult, paint, photo, background);
    }

    /**
     * Returns the avatar icon to use for this recipient entry. Returns null if we don't want to
     * draw an icon for this recipient.
     */
    private Bitmap getAvatarIcon(SearchResult searchResult) {
        try {
            return MediaStore.Images.Media.getBitmap(mContext
                    .getContentResolver(), searchResult.getPhoto());
        } catch (IOException e) {
            Log.e(TAG, "Couldn't get chip picture");
        }
        return null;
    }

    public static Bitmap getClip(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(output);
        Paint paint = new Paint();
        Rect rect = new Rect(0, 0, width, height);

        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);
        canvas.drawCircle(width / 2, height / 2, width / 2, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, null, rect, paint);
        return output;
    }

    private CharSequence createChip(SearchResult searchResult, boolean pressed) {
        String displayText = createSelectionText(searchResult);
        if (TextUtils.isEmpty(displayText)) {
            return null;
        }
        SpannableString chipText = null;
        // Always leave a blank space at the end of a chip.
        int textLength = displayText.length() - 1;
        chipText = new SpannableString(displayText);
        if (!mNoChips) {
            try {
                DrawableRecipientChip chip = constructChipSpan(searchResult, pressed);
                chipText.setSpan(chip, 0, textLength,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } catch (NullPointerException e) {
                Log.e(TAG, e.getMessage(), e);
                return null;
            }
        }
        return chipText;
    }

    private CharSequence ellipsizeText(CharSequence text, TextPaint paint, float maxWidth) {
        paint.setTextSize(mChipFontSize);
        if (maxWidth <= 0 && Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Max width is negative: " + maxWidth);
        }
        return TextUtils.ellipsize(text, paint, maxWidth,
                TextUtils.TruncateAt.END);
    }

    private DrawableRecipientChip constructChipSpan(SearchResult searchResult, boolean pressed)
            throws NullPointerException {
        if (mChipBackground == null) {
            throw new NullPointerException(
                    "Unable to render any chips as setChipDimensions was not called.");
        }

        TextPaint paint = getPaint();
        float defaultSize = paint.getTextSize();
        int defaultColor = paint.getColor();

        Bitmap tmpBitmap;
        if (pressed) {
            tmpBitmap = createSelectedChip(searchResult, paint);

        } else {
            tmpBitmap = createUnselectedChip(searchResult, paint);
        }

        // Pass the full text, un-ellipsized, to the chip.
        Drawable result = new BitmapDrawable(getResources(), tmpBitmap);
        result.setBounds(0, 0, tmpBitmap.getWidth(), tmpBitmap.getHeight());
        DrawableRecipientChip recipientChip =
                new VisibleRecipientChip(result, searchResult, getImageSpanAlignment());
        // Return text to the original size.
        paint.setTextSize(defaultSize);
        paint.setColor(defaultColor);
        return recipientChip;
    }

    private DrawableRecipientChip findChip(int offset) {
        DrawableRecipientChip[] chips =
                getSpannable().getSpans(0, getText().length(), DrawableRecipientChip.class);
        // Find the chip that contains this offset.
        for (int i = 0; i < chips.length; i++) {
            DrawableRecipientChip chip = chips[i];
            int start = getChipStart(chip);
            int end = getChipEnd(chip);
            if (offset >= start && offset <= end) {
                return chip;
            }
        }
        return null;
    }

    private DrawableRecipientChip getLastChip() {
        DrawableRecipientChip last = null;
        DrawableRecipientChip[] chips = getSortedRecipients();
        if (chips != null && chips.length > 0) {
            last = chips[chips.length - 1];
        }
        return last;
    }

    private DrawableRecipientChip[] getSortedRecipients() {
        DrawableRecipientChip[] recips = getSpannable()
                .getSpans(0, getText().length(), DrawableRecipientChip.class);
        ArrayList<DrawableRecipientChip> recipientsList = new ArrayList<DrawableRecipientChip>(
                Arrays.asList(recips));
        final Spannable spannable = getSpannable();
        Collections.sort(recipientsList, new Comparator<DrawableRecipientChip>() {

            @Override
            public int compare(DrawableRecipientChip first, DrawableRecipientChip second) {
                int firstStart = spannable.getSpanStart(first);
                int secondStart = spannable.getSpanStart(second);
                if (firstStart < secondStart) {
                    return -1;
                } else if (firstStart > secondStart) {
                    return 1;
                } else {
                    return 0;
                }
            }
        });
        return recipientsList.toArray(new DrawableRecipientChip[recipientsList.size()]);
    }

    /**
     * Show specified chip as selected. If the RecipientChip is just an email address,
     * selecting the chip will take the contents of the chip and place it at
     * the end of the RecipientEditTextView for inline editing. If the
     * RecipientChip is a complete contact, then selecting the chip
     * will change the background color of the chip, show the delete icon,
     * and a popup window with the address in use highlighted and any other
     * alternate addresses for the contact.
     * @param currentChip Chip to select.
     * @return A RecipientChip in the selected state or null if the chip
     * just contained an email address.
     */
    private DrawableRecipientChip selectChip(DrawableRecipientChip currentChip) {

        int start = getChipStart(currentChip);
        int end = getChipEnd(currentChip);
        getSpannable().removeSpan(currentChip);
        DrawableRecipientChip newChip;
        try {
            newChip = constructChipSpan(currentChip.getSearchResult(), true);
        } catch (NullPointerException e) {
            Log.e(TAG, e.getMessage(), e);
            return null;
        }
        Editable editable = getText();
        QwertyKeyListener.markAsReplaced(editable, start, end, "");
        if (start == -1 || end == -1) {
            Log.d(TAG, "The chip being selected no longer exists but should.");
        } else {
            editable.setSpan(newChip, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        newChip.setSelected(true);
        setCursorVisible(false);
        return newChip;
    }

    private Spannable getSpannable() {
        return getText();
    }

    String createSelectionText(SearchResult searchResult) {
        String name = searchResult.getName();
        String number = searchResult.getNumber();
        if (TextUtils.isEmpty(name) || TextUtils.equals(name, number)) {
            name = null;
        }
        String trimmedDisplayText;
        if (isPhoneNumber(number)) {
            trimmedDisplayText = number.trim();
        } else {
            if (number != null) {
                // Tokenize out the address in case the address already
                // contained the username as well.
                Rfc822Token[] tokenized = Rfc822Tokenizer.tokenize(number);
                if (tokenized.length > 0) {
                    number = tokenized[0].getAddress();
                }
            }
            Rfc822Token token = new Rfc822Token(name, number, null);
            trimmedDisplayText = token.toString().trim();
        }
        int index = trimmedDisplayText.indexOf(",");
        return mTokenizer != null && !TextUtils.isEmpty(trimmedDisplayText)
                && index < trimmedDisplayText.length() - 1 ? (String) mTokenizer
                .terminateToken(trimmedDisplayText) : trimmedDisplayText;
    }

    private SearchResult createTokenizedEntry(final String token) {
        if (TextUtils.isEmpty(token)) {
            return null;
        }
        if (isPhoneNumber(token)) {
            //TODO: Add the ability to construct a SearchResult from just a valid phone number.
            return SearchResult.constructResult(null, null, token, null);
        }
        Rfc822Token[] tokens = Rfc822Tokenizer.tokenize(token);
        String display = null;
        if (tokens.length > 0) {
            // If we can get a name from tokenizing, then generate an entry from
            // this.
            display = tokens[0].getName();
            if (!TextUtils.isEmpty(display)) {
                return SearchResult.constructResult(null, null, tokens[0].getAddress(), null);
            } else {
                display = tokens[0].getAddress();
                if (!TextUtils.isEmpty(display)) {
                    return SearchResult.constructResult(null, display, null, null);
                }
            }
        }
        return null;
    }

    private String createChipDisplayText(SearchResult searchResult) {
        String name = searchResult.getName();
        String number = searchResult.getNumber();
        if (!TextUtils.isEmpty(name)) {
            return name;
        } else if (!TextUtils.isEmpty(number)){
            return number;
        } else {
            return new Rfc822Token(name, number, null).toString();
        }
    }

    private class RecipientTextWatcher implements TextWatcher {

        @Override
        public void afterTextChanged(Editable s) {
            // If the text has been set to null or empty, make sure we remove
            // all the spans we applied.
            if (TextUtils.isEmpty(s)) {
                // Remove all the chips spans.
                Spannable spannable = getSpannable();
                DrawableRecipientChip[] chips = spannable.getSpans(0, getText().length(),
                        DrawableRecipientChip.class);
                for (DrawableRecipientChip chip : chips) {
                    spannable.removeSpan(chip);
                }

                clearSelectedChip();
                return;
            }

            // If the user is editing a chip, don't clear it.
            if (mSelectedChip != null) {
                setCursorVisible(true);
                setSelection(getText().length());
                clearSelectedChip();
            }
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            // The user deleted some text OR some text was replaced; check to
            // see if the insertion point is on a space
            // following a chip.
            if (before - count == 1) {
                // If the item deleted is a space, and the thing before the
                // space is a chip, delete the entire span.
                int selStart = getSelectionStart();
                DrawableRecipientChip[] repl = getSpannable().getSpans(selStart, selStart,
                        DrawableRecipientChip.class);
                if (repl.length > 0) {
                    // There is a chip there! Just remove it.
                    Editable editable = getText();
                    // Add the separator token.
                    int tokenStart = mTokenizer.findTokenStart(editable, selStart);
                    int tokenEnd = mTokenizer.findTokenEnd(editable, tokenStart);
                    tokenEnd = tokenEnd + 1;
                    if (tokenEnd > editable.length()) {
                        tokenEnd = editable.length();
                    }
                    editable.delete(tokenStart, tokenEnd);
                    getSpannable().removeSpan(repl[0]);
                }
            }
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            // Do nothing.
        }
    }

    private class ZyncTask extends AsyncTask<String, Void, Void> {

        private final String LOG_TAG = ZyncTask.class.getSimpleName();

        @Override
        protected Void doInBackground(String... params) {

            //If there's no userID, there's nothing to look up.  Verify size of params.
            if (params.length == 0) {
                return null;
            }


            // This will only happen if there was an error getting or parsing the forecast.
            return null;
        }



        @Override
        protected void onPostExecute(Void voids) {
            super.onPostExecute(voids);


        }
    }
}
