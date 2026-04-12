class MyAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var floatingStatus: TextView? = null

    private var step = 0
    private var pIdx = 1
    private var lastRun = ""

    private var lastActionTime = 0L
    private var scrollCount = 0
    private var avlHandled = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        showFloating()

        handler.postDelayed(object : Runnable {
            override fun run() {
                val prefs = getSharedPreferences("AutoData", MODE_PRIVATE)
                val target = prefs.getString("t_time", "11:00:00")!!
                val now = currentTime()

                floatingStatus?.text = "Time: $now | Step: $step"

                if (now == target && lastRun != now) {
                    rootInActiveWindow?.let {
                        safeClick(it, "Refresh")
                        safeClick(it, "Updated")
                        resetAll()
                        lastRun = now
                    }
                }

                handler.postDelayed(this, 150)
            }
        }, 150)
    }

    private fun resetAll() {
        step = 0
        pIdx = 1
        scrollCount = 0
        avlHandled = false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return

        val root = rootInActiveWindow ?: return
        if (!root.packageName.toString().contains("irctc")) return

        val prefs = getSharedPreferences("AutoData", MODE_PRIVATE)
        val baseDelay = (prefs.getString("c_delay", "200") ?: "200").toLong()
        val delay = baseDelay + (50..120).random()

        when (step) {
            0 -> findTrain(root, prefs, delay)
            1 -> checkAvailability(root, delay)
            2 -> fillForm(root, prefs, delay)
        }
    }

    private fun findTrain(root: AccessibilityNodeInfo, prefs: SharedPreferences, delay: Long) {
        val tNum = prefs.getString("t_num", "")!!
        val cls = prefs.getString("sel_cls", "SL")!!

        val node = root.findAccessibilityNodeInfosByText(tNum).firstOrNull()

        if (node != null) {
            scrollCount = 0
            val card = findCard(node)

            if (safeClick(card ?: root, cls)) {
                handler.postDelayed({ step = 1 }, delay)
            }

        } else {
            if (scrollCount < 10) {
                smartScroll(root)
                scrollCount++
            }
        }
    }

    private fun checkAvailability(root: AccessibilityNodeInfo, delay: Long) {
        if (!avlHandled && isAvailable(root)) {
            avlHandled = true

            handler.postDelayed({
                val r = rootInActiveWindow ?: return@postDelayed
                if (safeClick(r, "PASSENGER DETAILS")) {
                    step = 2
                }
            }, delay)
        }
    }

    private fun fillForm(root: AccessibilityNodeInfo, prefs: SharedPreferences, delay: Long) {
        safeClick(root, "OK")

        val edits = getEdits(root)
        val total = (1..6).count { !prefs.getString("n$it", "").isNullOrEmpty() }

        if (edits.size < 2) {
            handler.postDelayed({
                rootInActiveWindow?.let { fillForm(it, prefs, delay) }
            }, 200)
            return
        }

        if (edits[0].text.isNullOrEmpty()) {
            val name = prefs.getString("n$pIdx", "")!!
            val age = prefs.getString("a$pIdx", "")!!
            val gender = if (prefs.getString("g$pIdx", "M") == "F") "Female" else "Male"

            if (name.isNotEmpty()) {
                inputSafe(edits[0], name)
                inputSafe(edits[1], age)

                handler.postDelayed({
                    val r = rootInActiveWindow ?: return@postDelayed

                    if (safeClick(r, gender)) {
                        handler.postDelayed({
                            val r2 = rootInActiveWindow ?: return@postDelayed

                            if (safeClick(r2, "Add Passenger") || safeClick(r2, "ADD PASSENGER")) {
                                pIdx++
                            }

                        }, delay)
                    }

                }, delay)
            }

        } else if (pIdx > total && total > 0) {

            if (safeClick(root, "REVIEW JOURNEY DETAILS")) {
                handler.postDelayed({
                    val r = rootInActiveWindow ?: return@postDelayed
                    if (r.findAccessibilityNodeInfosByText("Add Passenger").isEmpty()) {
                        resetAll()
                    }
                }, 1500)
            }

        } else {

            if (safeClick(root, "Add New") || safeClick(root, "ADD NEW")) {
                // controlled click
            }
        }
    }

    // 🔥 MASTER CLICK ENGINE (DEBOUNCE + SAFE)
    private fun safeClick(root: AccessibilityNodeInfo?, text: String): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastActionTime < 300) return false

        val nodes = root?.findAccessibilityNodeInfosByText(text) ?: return false

        for (n in nodes) {
            var p: AccessibilityNodeInfo? = n
            var depth = 0

            while (p != null && depth < 6) {
                if (p.isClickable && p.isEnabled) {
                    p.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    lastActionTime = now
                    return true
                }
                p = p.parent
                depth++
            }
        }
        return false
    }

    private fun inputSafe(node: AccessibilityNodeInfo, text: String) {
        val b = Bundle()
        b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)

        handler.postDelayed({
            if (node.text?.toString() != text) {
                node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)
            }
        }, 120)
    }

    private fun isAvailable(root: AccessibilityNodeInfo): Boolean {
        return root.findAccessibilityNodeInfosByText("AVL").isNotEmpty() ||
               root.findAccessibilityNodeInfosByText("AVAILABLE").isNotEmpty()
    }

    private fun smartScroll(root: AccessibilityNodeInfo) {
        if (root.isScrollable) {
            root.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        } else {
            for (i in 0 until root.childCount) {
                val c = root.getChild(i)
                if (c?.isScrollable == true) {
                    c.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                    return
                }
            }
        }
    }

    private fun getEdits(r: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val list = mutableListOf<AccessibilityNodeInfo>()
        fun traverse(n: AccessibilityNodeInfo?) {
            if (n == null) return
            if (n.className == "android.widget.EditText") list.add(n)
            for (i in 0 until n.childCount) traverse(n.getChild(i))
        }
        traverse(r)
        return list
    }

    private fun findCard(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var p: AccessibilityNodeInfo? = n
        while (p?.parent != null) {
            p = p.parent
            if (p?.findAccessibilityNodeInfosByText("Refresh")?.isNotEmpty() == true) return p
        }
        return null
    }

    private fun currentTime(): String {
        val c = Calendar.getInstance()
        return String.format("%02d:%02d:%02d",
            c.get(Calendar.HOUR_OF_DAY),
            c.get(Calendar.MINUTE),
            c.get(Calendar.SECOND))
    }

    private fun showFloating() {
        try {
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            floatingStatus = TextView(this).apply {
                setBackgroundColor(Color.parseColor("#CC000000"))
                setTextColor(Color.WHITE)
                setPadding(20, 10, 20, 10)
                textSize = 14f
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )

            params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            params.y = 80

            wm.addView(floatingStatus, params)
        } catch (e: Exception) {}
    }

    override fun onInterrupt() {}
}
