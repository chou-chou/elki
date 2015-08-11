package de.lmu.ifi.dbs.elki.visualization.visualizers.parallel.cluster;

/*
 This file is part of ELKI:
 Environment for Developing KDD-Applications Supported by Index-Structures

 Copyright (C) 2015
 Ludwig-Maximilians-Universität München
 Lehr- und Forschungseinheit für Datenbanksysteme
 ELKI Development Team

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import java.util.Iterator;

import org.apache.batik.util.SVGConstants;
import org.w3c.dom.Element;

import de.lmu.ifi.dbs.elki.data.Cluster;
import de.lmu.ifi.dbs.elki.data.Clustering;
import de.lmu.ifi.dbs.elki.data.NumberVector;
import de.lmu.ifi.dbs.elki.data.model.MeanModel;
import de.lmu.ifi.dbs.elki.database.datastore.DataStoreListener;
import de.lmu.ifi.dbs.elki.visualization.VisualizationTask;
import de.lmu.ifi.dbs.elki.visualization.VisualizationTree;
import de.lmu.ifi.dbs.elki.visualization.VisualizerContext;
import de.lmu.ifi.dbs.elki.visualization.colors.ColorLibrary;
import de.lmu.ifi.dbs.elki.visualization.css.CSSClass;
import de.lmu.ifi.dbs.elki.visualization.projections.Projection;
import de.lmu.ifi.dbs.elki.visualization.projector.ParallelPlotProjector;
import de.lmu.ifi.dbs.elki.visualization.style.StyleLibrary;
import de.lmu.ifi.dbs.elki.visualization.svg.SVGPath;
import de.lmu.ifi.dbs.elki.visualization.svg.SVGPlot;
import de.lmu.ifi.dbs.elki.visualization.svg.SVGUtil;
import de.lmu.ifi.dbs.elki.visualization.visualizers.AbstractVisFactory;
import de.lmu.ifi.dbs.elki.visualization.visualizers.Visualization;
import de.lmu.ifi.dbs.elki.visualization.visualizers.parallel.AbstractParallelVisualization;

/**
 * Generates a SVG-Element that visualizes cluster means.
 *
 * @author Robert Rödler
 *
 * @apiviz.stereotype factory
 * @apiviz.uses Instance oneway - - «create»
 */
public class ClusterParallelMeanVisualization extends AbstractVisFactory {
  /**
   * A short name characterizing this Visualizer.
   */
  private static final String NAME = "Cluster Means";

  /**
   * Constructor.
   */
  public ClusterParallelMeanVisualization() {
    super();
  }

  @Override
  public Visualization makeVisualization(VisualizationTask task, SVGPlot plot, double width, double height, Projection proj) {
    return new Instance(task, plot, width, height, proj);
  }

  @Override
  public void processNewResult(VisualizerContext context, Object start) {
    VisualizationTree.findNewSiblings(context, start, Clustering.class, ParallelPlotProjector.class, //
    new VisualizationTree.Handler2<Clustering<?>, ParallelPlotProjector<?>>() {
      @Override
      public void process(VisualizerContext context, Clustering<?> c, ParallelPlotProjector<?> p) {
        if(c.getAllClusters().size() == 0) {
          return;
        }
        // Does the cluster have a model with cluster means?
        Clustering<MeanModel> mcls = findMeanModel(c);
        if(mcls == null) {
          return;
        }
        final VisualizationTask task = new VisualizationTask(NAME, context, c, p.getRelation(), ClusterParallelMeanVisualization.this);
        task.level = VisualizationTask.LEVEL_DATA + 1;
        context.addVis(c, task);
        context.addVis(p, task);
      }
    });
  }

  /**
   * Test if the given clustering has a mean model.
   *
   * @param c Clustering to inspect
   * @return the clustering cast to return a mean model, null otherwise.
   */
  @SuppressWarnings("unchecked")
  private static Clustering<MeanModel> findMeanModel(Clustering<?> c) {
    if(c.getAllClusters().get(0).getModel() instanceof MeanModel) {
      return (Clustering<MeanModel>) c;
    }
    return null;
  }

  @Override
  public boolean allowThumbnails(VisualizationTask task) {
    // Don't use thumbnails
    return false;
  }

  /**
   * Instance.
   *
   * @author Robert Rödler
   *
   */
  public class Instance extends AbstractParallelVisualization<NumberVector>implements DataStoreListener {
    /**
     * Generic tags to indicate the type of element. Used in IDs, CSS-Classes
     * etc.
     */
    public static final String CLUSTERMEAN = "Clustermean";

    /**
     * The result we visualize.
     */
    private Clustering<MeanModel> clustering;

    /**
     * Constructor.
     *
     * @param task VisualizationTask
     */
    public Instance(VisualizationTask task, SVGPlot plot, double width, double height, Projection proj) {
      super(task, plot, width, height, proj, ON_DATA | ON_STYLE);
      this.clustering = task.getResult();
      addListeners();
    }

    @Override
    protected void redraw() {
      super.redraw();
      addCSSClasses(svgp);

      Iterator<Cluster<MeanModel>> ci = clustering.getAllClusters().iterator();
      for(int cnum = 0; cnum < clustering.getAllClusters().size(); cnum++) {
        Cluster<MeanModel> clus = ci.next();
        if(clus.getModel() == null) {
          continue;
        }
        NumberVector mean = clus.getModel().getMean();
        if(mean == null) {
          continue;
        }

        double[] pmean = proj.fastProjectDataToRenderSpace(mean);

        SVGPath path = new SVGPath();
        for(int i = 0; i < pmean.length; i++) {
          path.drawTo(getVisibleAxisX(i), pmean[i]);
        }

        Element meanline = path.makeElement(svgp);
        SVGUtil.addCSSClass(meanline, CLUSTERMEAN + cnum);
        layer.appendChild(meanline);
      }
    }

    /**
     * Adds the required CSS-Classes.
     *
     * @param svgp SVG-Plot
     */
    private void addCSSClasses(SVGPlot svgp) {
      if(!svgp.getCSSClassManager().contains(CLUSTERMEAN)) {
        final StyleLibrary style = context.getStyleLibrary();
        ColorLibrary colors = style.getColorSet(StyleLibrary.PLOT);
        int clusterID = 0;

        for(@SuppressWarnings("unused")
        Cluster<?> cluster : clustering.getAllClusters()) {
          CSSClass cls = new CSSClass(this, CLUSTERMEAN + clusterID);
          cls.setStatement(SVGConstants.CSS_STROKE_WIDTH_PROPERTY, style.getLineWidth(StyleLibrary.PLOT) * 2.);

          final String color;
          if(clustering.getAllClusters().size() == 1) {
            color = SVGConstants.CSS_BLACK_VALUE;
          }
          else {
            color = colors.getColor(clusterID);
          }

          cls.setStatement(SVGConstants.CSS_STROKE_PROPERTY, color);
          cls.setStatement(SVGConstants.CSS_FILL_PROPERTY, SVGConstants.CSS_NONE_VALUE);

          svgp.addCSSClassOrLogError(cls);
          clusterID++;
        }
      }
    }
  }
}
