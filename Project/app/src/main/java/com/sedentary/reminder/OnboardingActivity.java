
package com.sedentary.reminder;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import java.util.Locale;

public class OnboardingActivity extends Activity {
    private static final int PAGE_SLEEP = 7;
    private static final int PAGE_DONE = 8;

    private Prefs p;
    private ViewFlipper flipper;
    private Button btnSkip, btnPrev, btnNext;
    private int page;
    private EditText etAge, etHeight, etWeight, etBodyFat, etSleepStart, etSleepEnd;
    private Spinner spGender, spJob;
    private CheckBox cbHealth;
    private TextView tvResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);
        p = new Prefs(this);
        flipper = findViewById(R.id.flipper);
        btnSkip = findViewById(R.id.btnSkip);
        btnPrev = findViewById(R.id.btnPrev);
        btnNext = findViewById(R.id.btnNext);

        buildPages();
        btnNext.setOnClickListener(v -> next());
        btnPrev.setOnClickListener(v -> {
            if (page > 0) {
                page--;
                setPage();
            }
        });
        btnSkip.setOnClickListener(v -> skip());
        setPage();
    }

    private void buildPages() {
        etAge = makeEdit("例如 28", android.text.InputType.TYPE_CLASS_NUMBER);
        etAge.setText(String.valueOf(p.age()));
        addPage("你的年龄？", "用于生成提醒间隔，可不填", etAge);

        etHeight = makeEdit("例如 175", android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etHeight.setText(String.valueOf(p.heightCm()));
        addPage("你的身高？", "单位 cm，可不填", etHeight);

        etWeight = makeEdit("例如 68", android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etWeight.setText(String.valueOf(p.weightKg()));
        addPage("你的体重？", "单位 kg，可不填", etWeight);

        etBodyFat = makeEdit("不知道就留空", android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etBodyFat.setText(p.bodyFat() > 0 ? String.valueOf(p.bodyFat()) : "");
        addPage("你的体脂率？", "单位 %，可不填", etBodyFat);

        spGender = makeSpinner(new String[]{"不填", "男", "女"}, p.gender());
        addPage("你的性别？", "用于体脂率判断，可不填", spGender);

        spJob = makeSpinner(new String[]{"久坐办公为主", "经常走动", "体力活动较多"}, p.occupation());
        addPage("日常活动类型？", "久坐越多，建议提醒越勤", spJob);

        cbHealth = new CheckBox(this);
        cbHealth.setText("血糖/血压偏高，或医生建议多活动");
        cbHealth.setTextColor(0xFF3F362E);
        cbHealth.setTextSize(16);
        cbHealth.setChecked(p.healthFlag());
        addPage("健康情况", "没有可不勾选，直接下一步", cbHealth);

        LinearLayout sleep = new LinearLayout(this);
        sleep.setOrientation(LinearLayout.HORIZONTAL);
        etSleepStart = makeEdit("入睡 0-23", android.text.InputType.TYPE_CLASS_NUMBER);
        etSleepEnd = makeEdit("起床 0-23", android.text.InputType.TYPE_CLASS_NUMBER);
        etSleepStart.setText(String.valueOf(p.quietStart()));
        etSleepEnd.setText(String.valueOf(p.quietEnd()));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        sp.setMargins(0, 0, dp(6), 0);
        sleep.addView(etSleepStart, sp);
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        ep.setMargins(dp(6), 0, 0, 0);
        sleep.addView(etSleepEnd, ep);
        addPage("你的睡眠时间？", "自动设为免打扰时段，可不填", sleep);

        LinearLayout done = new LinearLayout(this);
        done.setOrientation(LinearLayout.VERTICAL);
        done.setGravity(Gravity.CENTER);
        done.setPadding(dp(24), dp(24), dp(24), dp(24));
        TextView title = new TextView(this);
        title.setText("设置完成");
        title.setTextColor(0xFF3F362E);
        title.setTextSize(26);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        done.addView(title);
        tvResult = new TextView(this);
        tvResult.setTextColor(0xFF8A7F73);
        tvResult.setTextSize(15);
        tvResult.setGravity(Gravity.CENTER);
        tvResult.setLineSpacing(dp(4), 1f);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rp.setMargins(0, dp(18), 0, 0);
        done.addView(tvResult, rp);
        TextView note = new TextView(this);
        note.setText("这些信息只保存在本机，之后可在“设置 → 个人档案”中修改。");
        note.setTextColor(0xFFB0A698);
        note.setTextSize(12);
        note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        np.setMargins(0, dp(14), 0, 0);
        done.addView(note, np);
        flipper.addView(done);
    }

    private void addPage(String title, String subtitle, View input) {
        LinearLayout pageView = new LinearLayout(this);
        pageView.setOrientation(LinearLayout.VERTICAL);
        pageView.setPadding(dp(24), dp(30), dp(24), dp(24));
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(0xFF3F362E);
        t.setTextSize(25);
        t.setTypeface(null, Typeface.BOLD);
        pageView.addView(t);
        TextView s = new TextView(this);
        s.setText(subtitle);
        s.setTextColor(0xFF8A7F73);
        s.setTextSize(14);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sp.setMargins(0, dp(8), 0, 0);
        pageView.addView(s, sp);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ip.setMargins(0, dp(26), 0, 0);
        pageView.addView(input, ip);
        flipper.addView(pageView);
    }

    private EditText makeEdit(String hint, int inputType) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setInputType(inputType);
        et.setSingleLine(true);
        et.setBackgroundResource(R.drawable.edit_bg);
        et.setTextColor(0xFF3F362E);
        et.setTextSize(17);
        et.setPadding(dp(14), dp(12), dp(14), dp(12));
        return et;
    }

    private Spinner makeSpinner(String[] items, int selection) {
        Spinner sp = new Spinner(this);
        sp.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, items));
        sp.setSelection(selection);
        sp.setBackgroundResource(R.drawable.edit_bg);
        return sp;
    }

    private void next() {
        if (page < PAGE_SLEEP) {
            page++;
            setPage();
        } else if (page == PAGE_SLEEP) {
            computeAndShowDone();
        } else {
            finish();
        }
    }

    private void skip() {
        if (page == PAGE_SLEEP) {
            computeAndShowDone();
        } else if (page < PAGE_SLEEP) {
            page++;
            setPage();
        }
    }

    private void computeAndShowDone() {
        Integer age = parseOptional(etAge, 10, 100, "年龄");
        Integer h = parseOptional(etHeight, 100, 250, "身高");
        Integer w = parseOptional(etWeight, 30, 300, "体重");
        if (bad(age) || bad(h) || bad(w)) return;
        Integer bf = parseOptional(etBodyFat, 3, 60, "体脂率");
        Integer sleepS = parseOptional(etSleepStart, 0, 23, "入睡时间");
        Integer sleepE = parseOptional(etSleepEnd, 0, 23, "起床时间");
        if (bad(bf) || bad(sleepS) || bad(sleepE)) return;

        p.setAge(age != null ? age : p.age());
        p.setHeightCm(h != null ? h : p.heightCm());
        p.setWeightKg(w != null ? w : p.weightKg());
        p.setBodyFat(bf != null ? bf : p.bodyFat());
        p.setGender(spGender.getSelectedItemPosition());
        p.setOccupation(spJob.getSelectedItemPosition());
        p.setHealthFlag(cbHealth.isChecked());
        p.setQuietStart(sleepS != null ? sleepS : p.quietStart());
        p.setQuietEnd(sleepE != null ? sleepE : p.quietEnd());
        p.applyRecommendedProfile();

        tvResult.setText(String.format(Locale.CHINA,
                "BMI %.1f（%s）\n推荐：每 %d 分钟提醒 · %d 分钟内 %d 步\n免打扰：%02d:00 - %02d:00",
                p.bmi(), p.bmiLabel(), p.recommendedSitMinutes(),
                p.recommendedWinMinutes(), p.recommendedWinSteps(),
                p.quietStart(), p.quietEnd()));
        page = PAGE_DONE;
        setPage();
    }

    private void setPage() {
        flipper.setDisplayedChild(page);
        btnPrev.setVisibility(page == 0 ? View.GONE : View.VISIBLE);
        btnSkip.setVisibility(page >= PAGE_SLEEP ? View.GONE : View.VISIBLE);
        btnNext.setText(page == PAGE_DONE ? "完成" : "下一步");
    }

    private Integer parseOptional(EditText et, int lo, int hi, String name) {
        String s = et.getText().toString().trim();
        if (s.isEmpty()) return null;
        try {
            int v = (int) Double.parseDouble(s);
            if (v < lo || v > hi) {
                Toast.makeText(this, name + "需在 " + lo + "-" + hi, Toast.LENGTH_SHORT).show();
                return Integer.MIN_VALUE;
            }
            return v;
        } catch (NumberFormatException e) {
            Toast.makeText(this, name + "格式不对", Toast.LENGTH_SHORT).show();
            return Integer.MIN_VALUE;
        }
    }

    private boolean bad(Integer v) {
        return v != null && v == Integer.MIN_VALUE;
    }

    private int dp(int v) {
        return (int) (getResources().getDisplayMetrics().density * v);
    }
}
