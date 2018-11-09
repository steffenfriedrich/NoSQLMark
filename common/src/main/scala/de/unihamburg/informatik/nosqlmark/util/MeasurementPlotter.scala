package de.unihamburg.informatik.nosqlmark.util

import java.awt.{BasicStroke, Color, Font}
import javafx.scene.chart.Chart

import org.HdrHistogram.Histogram
import org.knowm.xchart.{SwingWrapper, XYChart, XYChartBuilder}
import org.knowm.xchart.XYSeries.XYSeriesRenderStyle
import org.knowm.xchart.style.Styler.{ChartTheme, LegendLayout, LegendPosition}
import org.knowm.xchart.style.markers.SeriesMarkers

object MeasurementPlotter {

  def plotXYChartToWindow(chart: XYChart) = new SwingWrapper[XYChart](chart).displayChart()

  def getXYChart(histograms: Seq[Histogram], labels: Seq[String]): XYChart = {
    val data = for(i <- 0 to histograms.size - 1) yield {
      val hist = histograms(i)
      val label = labels(i)
      val d = MeasurementUtil.getHdrHistogramData(hist)
      val xy = d.unzip
      Series(label, xy._1, xy._2)
    }
    getXYChart(data)
  }

  def getXYChart(data: Seq[Series]): XYChart =
    getXYChart(data, "Exponential distributed variables and drawn from HdrHistogram", "inter-request times (milliseconds)", "frequency")

  def getXYChart(data: Seq[Series], title: String, xAxisTitle: String, yAxisTitle: String): XYChart = {
    val chart = new XYChartBuilder().width(827).height(591).title(title).xAxisTitle(xAxisTitle).
      yAxisTitle(yAxisTitle).theme(ChartTheme.GGPlot2).build()

    val style = chart.getStyler
    // Customize Chart// Customize Chart
    //chart.getStyler.setPlotGridLinesVisible(false)
    //chart.getStyler.setXAxisTickMarkSpacingHint(100)

    style.setDefaultSeriesRenderStyle(XYSeriesRenderStyle.Line)
    style.setPlotMargin(0)
    // Markers
    style.setSeriesMarkers(Array(SeriesMarkers.NONE))
    style.setSeriesColors(Array(
      new Color(251,128,114),
      new Color(128,177,211),
      new Color(253,180,98),
      new Color(255,255,179),
      new Color(141,211,199),
      new Color(190,186,218),
      new Color(179,222,105),
      new Color(252,205,229),
      new Color(166,86,40)
    ))
    // Strokes

    style.setSeriesLines(Array(
      new BasicStroke(3f),
      new BasicStroke(3f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1.0f, Array(2f, 2f), 2.0f)
    ))
    // Axis
    style.setYAxisMax(null)
    style.setXAxisMax(null)
    style.setAxisTickLabelsColor(Color.BLACK) // new Coloter(130,130, 130)
    style.setAxisTickLabelsFont(style.getAxisTickLabelsFont.deriveFont(Font.PLAIN, 12))
    // Title
    style.setChartTitleBoxBackgroundColor(ChartColor.transparent)
    style.setChartTitleBoxBorderColor(ChartColor.transparent)
    style.setChartTitleBoxVisible(false)
    style.setChartTitlePadding(5)
    style.setChartTitleFont(style.getChartTitleFont.deriveFont(Font.PLAIN, 14))
    // Legend: OutsideE, InsideNW, InsideNE, InsideSE, InsideSW, InsideN, InsideS, OutsideS
    style.setLegendBackgroundColor(ChartColor.transparent)
    style.setLegendBorderColor(ChartColor.transparent)
    style.setLegendPosition(LegendPosition.InsideNE)
    style.setLegendLayout(LegendLayout.Vertical)
    style.setLegendPadding(10)
    // Grid and background colors
    style.setPlotBackgroundColor(Color.WHITE)
    style.setChartBackgroundColor(ChartColor.transparent)
    style.setPlotGridLinesColor(ChartColor.grid)
    //Border
    style.setPlotBorderVisible(true)
    style.setPlotBorderColor(Color.BLACK)



    // Series
    for(series <- data) {
      chart.addSeries(series.legendTitle, series.values, series.counts)
    }
    chart
  }

  case class Series(legendTitle: String, values: Array[Double], counts: Array[Double])

  def setAlpha(source: Color, alpha: Int) = new Color(source.getRed, source.getGreen, source.getBlue, alpha)

  case object ChartColor{
    val transparent = setAlpha(Color.WHITE,0)
    val grid = setAlpha(new Color(123, 123, 123),80)
  }
}
