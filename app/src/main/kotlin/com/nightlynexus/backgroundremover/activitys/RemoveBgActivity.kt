package com.nightlynexus.backgroundremover.activitys

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.core.net.toUri
import androidx.recyclerview.widget.LinearLayoutManager
import com.nightlynexus.backgroundremover.BackgroundPickerSheet
import com.nightlynexus.backgroundremover.adapter.ColorAdapter
import com.nightlynexus.backgroundremover.databinding.ActivityRemoveBgBinding
import com.nightlynexus.backgroundremover.extra.BackgroundRemover
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors


import android.graphics.*
import kotlin.math.max
import androidx.core.graphics.set



import android.app.Activity


// RenderScript imports
import android.renderscript.*






// RenderScript
import android.renderscript.*

class RemoveBgActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRemoveBgBinding

    private var originalBitmap: Bitmap? = null      // Always feathered base
    private var finalBitmap: Bitmap? = null          // Glow result shown to user

    private var selectedShadowColorBase: Int = Color.BLACK
    private var selectedShadowBlur: Float = 70f
    private var selectedShadowOpacity: Int = 80

    private val shadowColors = listOf(
            Color.BLACK, Color.WHITE, Color.RED, Color.GREEN,
            Color.BLUE, Color.CYAN, Color.MAGENTA, Color.YELLOW,
            Color.parseColor("#00E5FF"),
            Color.parseColor("#FF00E5"),
            Color.parseColor("#FFA500")
    )

    // -----------------------------------------------------------
    // SAFE FEATHER BLUR (RenderScript)
    // -----------------------------------------------------------
    private fun featherBitmap(src: Bitmap, radius: Float): Bitmap {

        val w = src.width
        val h = src.height

        val alphaBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val alphaCanvas = Canvas(alphaBmp)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.WHITE

        val alpha = src.extractAlpha()
        alphaCanvas.drawBitmap(alpha, 0f, 0f, paint)

        // RenderScript blur
        val rs = RenderScript.create(this)
        val input = Allocation.createFromBitmap(rs, alphaBmp)
        val output = Allocation.createTyped(rs, input.type)

        val blur = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
        blur.setRadius(radius.coerceIn(1f, 25f))
        blur.setInput(input)
        blur.forEach(output)

        output.copyTo(alphaBmp)
        rs.destroy()

        // Merge blurred alpha + original
        val final = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(final)

        canvas.drawBitmap(src, 0f, 0f, null)

        val xpaint = Paint(Paint.ANTI_ALIAS_FLAG)
        xpaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        canvas.drawBitmap(alphaBmp, 0f, 0f, xpaint)

        return final
    }


    // -----------------------------------------------------------
    // OUTER GLOW
    // -----------------------------------------------------------
    private fun createGlow(src: Bitmap, glowSize: Float, glowColor: Int): Bitmap {

        val w = src.width
        val h = src.height

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = glowColor
        paint.maskFilter = BlurMaskFilter(glowSize, BlurMaskFilter.Blur.OUTER)

        val alpha = src.extractAlpha()

        canvas.drawBitmap(alpha, 0f, 0f, paint)
        canvas.drawBitmap(src, 0f, 0f, null)

        return result
    }


    private val backgroundRemover by lazy {
        BackgroundRemover(Executors.newSingleThreadExecutor())
    }


    // -----------------------------------------------------------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRemoveBgBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Init shadow color list
        initColorRecycler()

        // Init working seekbars
        initShadowControls()

        // Buttons working
        binding.navSelect.setOnClickListener { pickImage() }
        binding.navBackground.setOnClickListener { openBackgroundSheet() }
        binding.navColor.setOnClickListener {
            binding.shadowControlsLayout.visibility = View.VISIBLE
        }
        binding.navAuto.setOnClickListener { applyAutoShadow() }
    }


    // -----------------------------------------------------------
    private fun pickImage() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, 100)
    }


    // -----------------------------------------------------------
    private fun openBackgroundSheet() {

        val sheet = BackgroundPickerSheet { type, data ->

            when (type) {

                "color" -> applyBackgroundColor(data.toInt())

                "drawable" -> applyBackgroundDrawable(data.toInt())

                "gallery" -> applyBackgroundGallery(data.toUri())

                "file" -> {
                    val bmp = BitmapFactory.decodeFile(data)
                    finalBitmap?.let { applyBackgroundWithBitmap(it, bmp) }
                }
            }
        }

        sheet.show(supportFragmentManager, "bgPickerSheet")
    }


    // -----------------------------------------------------------
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 100 && resultCode == Activity.RESULT_OK) {

            val uri = data?.data ?: return

            binding.progress.visibility = View.VISIBLE

            backgroundRemover.removeBackground(
                    this,
                    uri,
                    object : BackgroundRemover.Callback {

                        override fun onSuccess(
                                imageUri: Uri,
                                fileName: String,
                                foregroundToDisplay: Bitmap,
                                foregroundToSave: Bitmap
                        ) {

                            binding.progress.visibility = View.GONE

                            // ⭐ AUTO 90% FEATHER (32f)
                            val feathered = featherBitmap(foregroundToSave, 32f)

                            originalBitmap = feathered       // ALWAYS feathered base
                            finalBitmap = feathered

                            binding.outputImage.setImageBitmap(feathered)
                        }

                        override fun onFailure(imageUri: Uri, e: Exception) {
                            binding.progress.visibility = View.GONE
                            Toast.makeText(
                                    this@RemoveBgActivity,
                                    "Error: ${e.message}",
                                    Toast.LENGTH_LONG
                            ).show()
                        }
                    }
            )
        }
    }


    // -----------------------------------------------------------
    private fun initShadowControls() {

        // BLUR SLIDER
        binding.blurSeekBar.setOnSeekBarChangeListener(object :
                SeekBar.OnSeekBarChangeListener {

            override fun onProgressChanged(sb: SeekBar?, p: Int, f: Boolean) {
                selectedShadowBlur = p.toFloat()
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) { applySelectedShadow() }
        })


        // OPACITY SLIDER
        binding.opacitySeekBar.setOnSeekBarChangeListener(object :
                SeekBar.OnSeekBarChangeListener {

            override fun onProgressChanged(sb: SeekBar?, p: Int, f: Boolean) {
                selectedShadowOpacity = p
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) { applySelectedShadow() }
        })
    }


    // -----------------------------------------------------------
    private fun initColorRecycler() {

        binding.colorRecycler.layoutManager =
                LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        val adapter = ColorAdapter(shadowColors) { color ->
            selectedShadowColorBase = color
            applySelectedShadow()
        }

        binding.colorRecycler.adapter = adapter
    }


    // -----------------------------------------------------------
    private fun applySelectedShadow() {

        val src = originalBitmap ?: return    // ALWAYS feathered

        val glowColor = applyOpacity(selectedShadowColorBase, selectedShadowOpacity)

        // ⭐ Glow always applied over feathered image
        val final = createGlow(
                src = src,
                glowSize = selectedShadowBlur,
                glowColor = glowColor
        )

        finalBitmap = final
        binding.outputImage.setImageBitmap(final)
    }


    // -----------------------------------------------------------
    private fun applyAutoShadow() {

        selectedShadowColorBase = Color.BLACK
        selectedShadowBlur = 75f
        selectedShadowOpacity = 85

        binding.blurSeekBar.progress = 75
        binding.opacitySeekBar.progress = 85

        applySelectedShadow()
    }


    private fun applyOpacity(color: Int, percent: Int): Int {
        val alpha = (percent * 255 / 100)
        return (color and 0x00FFFFFF) or (alpha shl 24)
    }


    // -----------------------------------------------------------
    // BACKGROUND APPLY
    // -----------------------------------------------------------
    private fun applyBackgroundColor(color: Int) {
        finalBitmap?.let { applyBackground(it, color) }
    }

    private fun applyBackgroundDrawable(drawableRes: Int) {
        val bg = BitmapFactory.decodeResource(resources, drawableRes)
        finalBitmap?.let { applyBackgroundWithBitmap(it, bg) }
    }

    private fun applyBackgroundGallery(uri: Uri) {
        val bmp = MediaStore.Images.Media.getBitmap(contentResolver, uri)
        finalBitmap?.let { applyBackgroundWithBitmap(it, bmp) }
    }

    private fun applyBackground(shadow: Bitmap, bgColor: Int) {

        val out = Bitmap.createBitmap(shadow.width, shadow.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        canvas.drawColor(bgColor)
        canvas.drawBitmap(shadow, 0f, 0f, null)

        finalBitmap = out
        binding.outputImage.setImageBitmap(out)
    }

    private fun applyBackgroundWithBitmap(shadow: Bitmap, bg: Bitmap) {

        val bgScaled = Bitmap.createScaledBitmap(bg, shadow.width, shadow.height, true)

        val out = Bitmap.createBitmap(shadow.width, shadow.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        canvas.drawBitmap(bgScaled, 0f, 0f, null)
        canvas.drawBitmap(shadow, 0f, 0f, null)

        finalBitmap = out
        binding.outputImage.setImageBitmap(out)
    }
}
