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

import androidx.recyclerview.widget.LinearLayoutManager
import com.nightlynexus.backgroundremover.BackgroundPickerSheet
import com.nightlynexus.backgroundremover.adapter.ColorAdapter
import com.nightlynexus.backgroundremover.databinding.ActivityRemoveBgBinding
import com.nightlynexus.backgroundremover.extra.BackgroundRemover

import java.util.concurrent.Executors


import android.graphics.*


import android.app.Activity

import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur



class RemoveBgActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRemoveBgBinding

    private var originalBitmap: Bitmap? = null
    private var finalBitmap: Bitmap? = null

    private var isOutlineMode: Boolean = false
    private var isRemoveBackGround: Boolean = false

    private var selectedShadowColorBase: Int = Color.BLACK
    private var selectedShadowBlur: Float = 40f
    private var selectedShadowOpacity: Int = 60

    private var selectedOutlineThickness: Float = 10f
    private var selectedDashGap: Float = 10f
    private val fixedDashLength: Float = 20f

    private val shadowColors = listOf(
            Color.BLACK, Color.WHITE, Color.RED, Color.GREEN,
            Color.BLUE, Color.CYAN, Color.MAGENTA, Color.YELLOW
    )

    private val backgroundRemover by lazy {
        BackgroundRemover(Executors.newSingleThreadExecutor())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRemoveBgBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initControls()
        initColorRecycler()

        isOutlineMode = false
        binding.shadowControlsLayout.visibility = View.VISIBLE

        binding.navSelect.setOnClickListener { pickImage() }

        binding.navBackground.setOnClickListener {
            isOutlineMode = true
 //            applyEffect()
        }

        binding.navColor.setOnClickListener {
            isOutlineMode = false
            binding.shadowControlsLayout.visibility = View.VISIBLE
             applyEffect()
        }

        binding.navAuto.setOnClickListener { applyAutoShadow() }
    }

    private fun pickImage() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, 100)
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)

        if (req == 100 && res == Activity.RESULT_OK) {

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
                            isRemoveBackGround = true
                            binding.progress.visibility = View.GONE

                            val feathered = featherBitmap(foregroundToSave, 32f)

                            originalBitmap = feathered
                            finalBitmap = feathered

                            binding.outputImage.setImageBitmap(feathered)

                            applyEffect()
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

    private fun featherBitmap(src: Bitmap, radius: Float): Bitmap {

        val w = src.width
        val h = src.height

        val alphaBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val alphaCanvas = Canvas(alphaBmp)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.WHITE

        val alpha = src.extractAlpha()
        alphaCanvas.drawBitmap(alpha, 0f, 0f, paint)

        val rs = RenderScript.create(this)
        val input = Allocation.createFromBitmap(rs, alphaBmp)
        val output = Allocation.createTyped(rs, input.type)

        val blur = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
        blur.setRadius(radius.coerceIn(1f, 25f))
        blur.setInput(input)
        blur.forEach(output)

        output.copyTo(alphaBmp)
        rs.destroy()

        val final = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(final)

        canvas.drawBitmap(src, 0f, 0f, null)

        val xpaint = Paint(Paint.ANTI_ALIAS_FLAG)
        xpaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        canvas.drawBitmap(alphaBmp, 0f, 0f, xpaint)

        return final
    }

    private fun initControls() {

        binding.blurSeekBar.setOnSeekBarChangeListener(object :
                SeekBar.OnSeekBarChangeListener {

            override fun onProgressChanged(sb: SeekBar?, p: Int, f: Boolean) {
                selectedShadowBlur = p.toFloat()
                selectedOutlineThickness = p.toFloat() * 0.4f + 1f
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {
                applyEffect()
            }
        })

        binding.opacitySeekBar.setOnSeekBarChangeListener(object :
                SeekBar.OnSeekBarChangeListener {

            override fun onProgressChanged(sb: SeekBar?, p: Int, f: Boolean) {
                selectedShadowOpacity = p
                selectedDashGap = p.toFloat() * 0.5f + 5f
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {
                applyEffect()
            }
        })
    }

    private fun initColorRecycler() {
        binding.colorRecycler.layoutManager =
                LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        binding.colorRecycler.adapter =
                ColorAdapter(shadowColors) { color ->
                    selectedShadowColorBase = color
                    applyEffect()
                }
    }

    private fun applyEffect() {
        if (originalBitmap == null) return

        if (isOutlineMode) {
            applyDottedOutline()
        } else {
            applyShadow()
        }
    }

    private fun applyShadow() {
        val src = originalBitmap ?: return

        val glowColor = applyOpacity(selectedShadowColorBase, selectedShadowOpacity)

        val result = createGlow(
                src = src,
                glowSize = selectedShadowBlur,
                glowColor = glowColor
        )

        finalBitmap = result
        binding.outputImage.setImageBitmap(result)
    }

    private fun createGlow(src: Bitmap, glowSize: Float, glowColor: Int): Bitmap {

        val w = src.width
        val h = src.height
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

        val canvas = Canvas(result)
        val alpha = src.extractAlpha()

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = glowColor
            maskFilter = BlurMaskFilter(glowSize, BlurMaskFilter.Blur.OUTER)
        }

        canvas.drawBitmap(alpha, 0f, 0f, paint)
        canvas.drawBitmap(src, 0f, 0f, null)

        return result
    }

    private fun applyAutoShadow() {
        isOutlineMode = false
        binding.shadowControlsLayout.visibility = View.VISIBLE

        selectedShadowColorBase = Color.BLACK
        selectedShadowBlur = 75f
        selectedShadowOpacity = 85

        binding.blurSeekBar.progress = 75
        binding.opacitySeekBar.progress = 85

        applyShadow()
    }

    private fun applyOpacity(color: Int, percent: Int): Int {
        val alpha = percent * 255 / 100
        return (color and 0x00FFFFFF) or (alpha shl 24)
    }

    private fun applyDottedOutline() {
        val src = originalBitmap ?: return

        val outlineColor = selectedShadowColorBase
        val thickness = selectedOutlineThickness.coerceAtLeast(3f)
        val dashGap = selectedDashGap.coerceAtLeast(10f)

        val result = createDottedOutline(
                src = src,
                thickness = thickness,
                outlineColor = outlineColor,
                dashLength = fixedDashLength.coerceAtLeast(20f),
                dashGap = dashGap
        )

        finalBitmap = result
        binding.outputImage.setImageBitmap(result)
    }

    private fun createDottedOutline(
            src: Bitmap,
            thickness: Float,
            outlineColor: Int,
            dashLength: Float,
            dashGap: Float
    ): Bitmap {

        val w = src.width
        val h = src.height
        val alpha = src.extractAlpha()

        val finalResult = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val finalCanvas = Canvas(finalResult)

        val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = outlineColor
            strokeWidth = thickness
            maskFilter = null
            pathEffect = if (dashLength > 0f && dashGap > 0f)
                DashPathEffect(floatArrayOf(dashLength, dashGap), 0f)
            else null
        }

        finalCanvas.drawBitmap(alpha, 0f, 0f, dashPaint)
        finalCanvas.drawBitmap(src, 0f, 0f, null)

        return finalResult
    }
}
