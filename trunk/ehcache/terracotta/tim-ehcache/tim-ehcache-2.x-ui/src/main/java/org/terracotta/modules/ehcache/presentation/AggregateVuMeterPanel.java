/*
 * All content copyright (c) Terracotta, Inc., except as may otherwise be noted in a separate copyright notice. All
 * rights reserved.
 */
package org.terracotta.modules.ehcache.presentation;

import org.terracotta.modules.ehcache.presentation.model.CacheManagerModel;

import com.tc.admin.AbstractClusterListener;
import com.tc.admin.common.ApplicationContext;
import com.tc.admin.common.XContainer;
import com.tc.admin.model.ClientConnectionListener;
import com.tc.admin.model.IClient;
import com.tc.admin.model.IClusterModel;
import com.tc.admin.model.IServer;
import com.tc.admin.model.PolledAttributeListener;
import com.tc.admin.model.PolledAttributesResult;
import com.tc.admin.options.RuntimeStatsOption;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.management.ObjectName;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;

public class AggregateVuMeterPanel extends XContainer implements PolledAttributeListener, ClientConnectionListener,
    Runnable {
  private static final String      GLOBAL_MODE_CACHE_NAME        = "";

  private final static int         VU_HIT_HEIGHT                 = 10;
  private final static int         VU_MISS_HEIGHT                = 10;
  private final static int         VU_PUT_HEIGHT                 = 10;
  private final static int         VU_HEIGHT                     = VU_HIT_HEIGHT + VU_PUT_HEIGHT + VU_MISS_HEIGHT;
  private final static int         VU_X_MARGIN_LEFT              = 55;
  private final static int         VU_X_SPACING                  = 5;
  private final static int         VU_X_MARGIN_RIGHT             = 55;
  private final static int         VU_Y_SPACING                  = 35;
  private final static int         VU_MAX_DECAY_POLL_MULTIPLE    = 3;

  private final static int         ANIMATION_POLL_PERIOD_DIVIDER = 15;
  private final static int         ANIMATION_FPS                 = 15;
  private final static int         ANIMATION_THREAD_SLEEP        = (int) (1000L / ANIMATION_FPS);

  private final static Color       VU_BACKGROUND_COLOR           = Color.WHITE;
  private final static Color       VU_HIT_FILL_COLOR             = EhcachePresentationUtils.HIT_FILL_COLOR;
  private final static Color       VU_MISS_FILL_COLOR            = EhcachePresentationUtils.MISS_FILL_COLOR;
  private final static Color       VU_PUT_FILL_COLOR             = EhcachePresentationUtils.PUT_FILL_COLOR;
  private final static Color       VU_HIT_DRAW_COLOR             = EhcachePresentationUtils.HIT_DRAW_COLOR;
  private final static Color       VU_MISS_DRAW_COLOR            = EhcachePresentationUtils.MISS_DRAW_COLOR;
  private final static Color       VU_PUT_DRAW_COLOR             = EhcachePresentationUtils.PUT_DRAW_COLOR;

  private final static String      CACHE_METRICS_ATTR            = "CacheMetrics";

  private final static String[]    POLLED_ATTRS                  = { CACHE_METRICS_ATTR };

  private final static Set<String> POLLED_ATTRS_SET              = new HashSet<String>(Arrays.asList(POLLED_ATTRS));

  private final ApplicationContext appContext;
  private final boolean            globalMode;
  private final IClusterModel      clusterModel;
  private final ClusterListener    clusterListener;
  private final ObjectName         tmplName;

  private CacheSamplesPanel        cacheSamplesPanel;
  private JScrollPane              cacheSamplesScrollPane;

  protected AggregateVuMeterPanel(ApplicationContext appContext, CacheManagerModel cacheManagerModel, boolean globalMode) {
    this.appContext = appContext;
    this.clusterModel = cacheManagerModel.getClusterModel();
    this.globalMode = globalMode;
    this.clusterListener = new ClusterListener(clusterModel);
    try {
      this.tmplName = EhcacheStatsUtils.getSampledCacheManagerBeanName(cacheManagerModel.getName());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    setLayout(new BorderLayout());
    setupCacheSamplesPanel();
  }

  public void run() {
    if (cacheSamplesPanel != null) {
      cacheSamplesPanel.repaint();
    }
  }

  public void setup() {
    IServer activeCoord = clusterModel.getActiveCoordinator();
    if (activeCoord != null) {
      activeCoord.addClientConnectionListener(this);
    }
    clusterModel.addPropertyChangeListener(clusterListener);
    if (clusterModel.isReady()) {
      init();
    }
  }

  private class ClusterListener extends AbstractClusterListener {
    private ClusterListener(IClusterModel clusterModel) {
      super(clusterModel);
    }

    @Override
    protected void handleReady() {
      if (clusterModel.isReady()) {
        init();
      } else {
        suspend();
      }
    }

    @Override
    protected void handleActiveCoordinator(IServer oldActive, IServer newActive) {
      if (oldActive != null) {
        oldActive.removeClientConnectionListener(AggregateVuMeterPanel.this);
      }
      if (newActive != null) {
        newActive.addClientConnectionListener(AggregateVuMeterPanel.this);
      }
    }

    @Override
    protected void handleUncaughtError(Exception e) {
      appContext.log(e);
    }
  }

  protected void init() {
    IServer activeCoord = clusterModel.getActiveCoordinator();
    if (activeCoord != null) {
      activeCoord.addClientConnectionListener(this);
    }

    if (!cacheSamplesPanel.isStarted()) {
      cacheSamplesPanel.start();
      addPolledAttributeListener();
    }
  }

  public void suspend() {
    removePolledAttributeListener();
    cacheSamplesPanel.stop();
  }

  public void clientConnected(IClient client) {
    ObjectName on = client.getTunneledBeanName(tmplName);
    client.addPolledAttributeListener(on, POLLED_ATTRS_SET, this);
  }

  public void clientDisconnected(IClient client) {
    ObjectName on = client.getTunneledBeanName(tmplName);
    client.removePolledAttributeListener(on, POLLED_ATTRS_SET, this);
  }

  protected void addPolledAttributeListener() {
    for (IClient client : clusterModel.getClients()) {
      clientConnected(client);
    }
  }

  protected void removePolledAttributeListener() {
    for (IClient client : clusterModel.getClients()) {
      clientDisconnected(client);
    }
  }

  private void setupCacheSamplesPanel() {
    cacheSamplesPanel = new CacheSamplesPanel();

    // only add scroll bars in per-cache mode
    if (globalMode) {
      add(cacheSamplesPanel);
    } else {
      cacheSamplesScrollPane = new JScrollPane(cacheSamplesPanel);
      cacheSamplesScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
      cacheSamplesScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
      cacheSamplesScrollPane.getVerticalScrollBar().setUnitIncrement(16);

      add(cacheSamplesScrollPane);
    }

    repaint();
  }

  private static class MaxLevel {
    private final long value;
    private final long moment;

    public MaxLevel(long value) {
      this.value = value;
      this.moment = System.currentTimeMillis();
    }

    public long getValue() {
      return value;
    }

    public long getMoment() {
      return moment;
    }
  }

  private void repaintCacheSamplesPanel() {
    SwingUtilities.invokeLater(this);
  }

  class CacheSamplesPanel extends JPanel implements Runnable {
    private long                          lastCacheSamplesUpdate = System.currentTimeMillis();
    private double                        lastScaleFactor        = -1d;
    private volatile Map<String, long[]>  currentCacheSamples;
    private volatile Map<String, long[]>  previousCacheRenders;
    private volatile Map<String, long[]>  currentCacheRenders;

    private final Map<String, MaxLevel[]> maxLevels              = new HashMap<String, MaxLevel[]>();

    private Thread                        thread;

    CacheSamplesPanel() {
      super();
      setOpaque(false);
    }

    public synchronized boolean isStarted() {
      return thread != null;
    }

    public synchronized void start() {
      if (!isStarted()) {
        thread = new Thread(this);
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
      }
    }

    public synchronized void stop() {
      Thread theThread = thread;
      thread = null;
      if (theThread != null) {
        theThread.interrupt();
      }
    }

    private synchronized Thread getThread() {
      return thread;
    }

    @Override
    public void removeNotify() {
      super.removeNotify();
      stop();
    }

    public void run() {
      Thread me = Thread.currentThread();
      while (getThread() == me) {
        repaintCacheSamplesPanel();

        try {
          Thread.sleep(ANIMATION_THREAD_SLEEP);
        } catch (InterruptedException e) {
          return;
        }
      }
      thread = null;
    }

    private long getPollPeriodMillis() {
      RuntimeStatsOption runtimeStatsOption = (RuntimeStatsOption) appContext.getOption(RuntimeStatsOption.NAME);
      if (runtimeStatsOption != null) { return runtimeStatsOption.getPollPeriodSeconds() * 1000; }
      return 0L;
    }

    private synchronized Map<String, long[]> calculateRenderedSamples(double scaleFactor) {
      Map<String, long[]> calculatedSamples = new TreeMap<String, long[]>();
      if (null == currentCacheSamples || currentCacheSamples.isEmpty()) { return calculatedSamples; }

      long animationPeriod = getPollPeriodMillis() / ANIMATION_POLL_PERIOD_DIVIDER;

      if (null == currentCacheRenders || lastScaleFactor != scaleFactor) {
        Map<String, long[]> renders = new TreeMap<String, long[]>();
        for (Map.Entry<String, long[]> entry : currentCacheSamples.entrySet()) {
          long[] current = entry.getValue();
          long[] calculated = new long[current.length];
          for (int i = 0; i < current.length; i++) {
            calculated[i] = (long) (current[i] * scaleFactor);
          }
          renders.put(entry.getKey(), calculated);
        }
        currentCacheRenders = renders;
      }

      long timeSinceLastUpdate = System.currentTimeMillis() - lastCacheSamplesUpdate;
      if (lastScaleFactor != scaleFactor) {
        lastScaleFactor = scaleFactor;
        return currentCacheRenders;
      } else if (null == previousCacheRenders || timeSinceLastUpdate > animationPeriod) {
        return currentCacheRenders;
      } else {
        // interpolate the data to create an animation
        double animationCoefficient = ((double) timeSinceLastUpdate) / animationPeriod;

        for (Map.Entry<String, long[]> entry : currentCacheRenders.entrySet()) {
          long[] previousScaled = previousCacheRenders.get(entry.getKey());
          long[] currentScaled = entry.getValue();
          // compensate for missing previous values
          if (null == previousScaled) {
            previousScaled = currentScaled;
          }

          // interpolate the scaled data taking the animation coefficient into account
          long[] calculated = new long[currentScaled.length];
          for (int i = 0; i < currentScaled.length; i++) {
            long calculatedValue = (long) (previousScaled[i] + ((currentScaled[i] - previousScaled[i]) * animationCoefficient));
            calculated[i] = calculatedValue;
          }

          calculatedSamples.put(entry.getKey(), calculated);
        }

        return calculatedSamples;
      }
    }

    private int getVerticalAlignment(int heightToAlignTo, FontMetrics metrics) {
      return ((heightToAlignTo - metrics.getHeight()) / 2) + metrics.getDescent();
    }

    @Override
    public synchronized void paint(Graphics g) {
      final Graphics2D g2 = (Graphics2D) g;
      final Dimension size = getSize();

      g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

      // g2.setColor(getBackground());
      // g2.fillRect(0, 0, size.width, size.height);

      Font statsFont = new Font("SansSerif", Font.PLAIN, 11);
      FontMetrics statsFontMetrics = g2.getFontMetrics(statsFont);

      Font cacheNamesFont = new Font("SansSerif", Font.PLAIN, 12);
      FontMetrics cacheNamesFontMetrics = g2.getFontMetrics(cacheNamesFont);

      if (currentCacheSamples != null) {

        // determined the top rate and keep track of max levels of each individual rate
        long topRate = 0;
        for (Map.Entry<String, long[]> entry : currentCacheSamples.entrySet()) {
          // determine the top rate based on hit
          if (entry.getValue()[0] > topRate) {
            topRate = entry.getValue()[0];
          }

          // determine the top rate based on miss
          if (entry.getValue()[1] > topRate) {
            topRate = entry.getValue()[1];
          }

          // keep track of the max levels
          final long[] currentLevels = entry.getValue();
          final MaxLevel[] previousMaxLevels = maxLevels.get(entry.getKey());
          final MaxLevel[] currentMaxLevels = new MaxLevel[3];
          if (null == previousMaxLevels) {
            for (int i = 0; i < currentLevels.length; i++) {
              currentMaxLevels[i] = new MaxLevel(currentLevels[i]);
            }
          } else {
            // calculate the maximum level expiration as being three times the poll period
            long maxLevelExpiration = getPollPeriodMillis() * VU_MAX_DECAY_POLL_MULTIPLE;

            // detect the current maximum levels
            for (int i = 0; i < currentLevels.length; i++) {
              if ((maxLevelExpiration > 0 && (System.currentTimeMillis() - previousMaxLevels[i].getMoment()) > maxLevelExpiration)
                  || currentLevels[i] > previousMaxLevels[i].getValue()) {
                currentMaxLevels[i] = new MaxLevel(currentLevels[i]);
              } else {
                currentMaxLevels[i] = previousMaxLevels[i];
              }
            }
          }
          maxLevels.put(entry.getKey(), currentMaxLevels);

          // determine the top rate based on max levels
          for (MaxLevel currentMaxLevel : currentMaxLevels) {
            if (currentMaxLevel.getValue() > topRate) {
              topRate = currentMaxLevel.getValue();
            }
          }
        }

        // calculate the maximum width
        final int maxWidth = (int) size.getWidth() - (VU_X_MARGIN_LEFT + VU_X_MARGIN_RIGHT);

        // calculate the scale factor to prevent clipping
        double scaleFactor = 1d;
        if (topRate > 0) {
          scaleFactor = ((double) maxWidth) / topRate;
        }

        // horizontally draw the graphs for each cache
        int y = 0;

        for (Map.Entry<String, long[]> entry : calculateRenderedSamples(scaleFactor).entrySet()) {
          y += VU_Y_SPACING;

          // only draw the cache name when global mode is not on
          if (globalMode) {
            y -= cacheNamesFontMetrics.getHeight();
          } else {
            String cacheName = entry.getKey().toString();
            g2.setFont(cacheNamesFont);
            g2.setColor(Color.BLACK);
            g2.drawString(cacheName, VU_X_MARGIN_LEFT, y - 4);
          }

          // draw the VU background
          g2.setFont(statsFont);
          g2.setColor(VU_BACKGROUND_COLOR);
          g2.fillRect(VU_X_MARGIN_LEFT, y, maxWidth, VU_HEIGHT);

          // calculate and draw the hit rate graph
          final long hitWidth = entry.getValue()[0];

          g2.setColor(VU_HIT_FILL_COLOR);
          g2.fillRect(VU_X_MARGIN_LEFT, y, (int) hitWidth, VU_HIT_HEIGHT);

          y += VU_HIT_HEIGHT;

          String currentHitText = String.valueOf(currentCacheSamples.get(entry.getKey())[0]);
          final int currentHitTextWidth = statsFontMetrics.stringWidth(currentHitText);
          g2.setColor(VU_HIT_DRAW_COLOR);
          g2.drawString(currentHitText, VU_X_MARGIN_LEFT - VU_X_SPACING - currentHitTextWidth,
                        1 + y - getVerticalAlignment(VU_HIT_HEIGHT, statsFontMetrics));

          // calculate and draw the miss rate graph
          final long missWidth = entry.getValue()[1];

          g2.setColor(VU_MISS_FILL_COLOR);
          g2.fillRect(VU_X_MARGIN_LEFT, y, (int) missWidth, VU_MISS_HEIGHT);

          y += VU_MISS_HEIGHT;

          String currentMissText = String.valueOf(currentCacheSamples.get(entry.getKey())[1]);
          final int currentMissTextWidth = statsFontMetrics.stringWidth(currentMissText);
          g2.setColor(VU_MISS_DRAW_COLOR);
          g2.drawString(currentMissText, VU_X_MARGIN_LEFT - VU_X_SPACING - currentMissTextWidth,
                        1 + y - getVerticalAlignment(VU_MISS_HEIGHT, statsFontMetrics));

          // calculate and draw the put rate graph
          final long putWidth = entry.getValue()[2];

          g2.setColor(VU_PUT_FILL_COLOR);
          g2.fillRect(VU_X_MARGIN_LEFT, y, (int) putWidth, VU_PUT_HEIGHT);

          y += VU_PUT_HEIGHT;

          String currentPutText = String.valueOf(currentCacheSamples.get(entry.getKey())[2]);
          final int currentPutTextWidth = statsFontMetrics.stringWidth(currentPutText);
          g2.setColor(VU_PUT_DRAW_COLOR);
          g2.drawString(currentPutText, VU_X_MARGIN_LEFT - VU_X_SPACING - currentPutTextWidth,
                        1 + y - getVerticalAlignment(VU_PUT_HEIGHT, statsFontMetrics));

          final int adaptedY = y - VU_HEIGHT;

          // draw the wire frame
          g2.setColor(Color.BLACK);
          g2.drawLine(VU_X_MARGIN_LEFT, adaptedY, maxWidth + VU_X_MARGIN_LEFT, adaptedY);
          g2.drawLine(VU_X_MARGIN_LEFT, adaptedY + VU_HIT_HEIGHT, maxWidth + VU_X_MARGIN_LEFT, adaptedY + VU_HIT_HEIGHT);
          g2.drawLine(VU_X_MARGIN_LEFT, adaptedY + VU_HIT_HEIGHT + VU_MISS_HEIGHT, maxWidth + VU_X_MARGIN_LEFT,
                      adaptedY + VU_HIT_HEIGHT + VU_MISS_HEIGHT);
          g2.drawLine(VU_X_MARGIN_LEFT, adaptedY + VU_HEIGHT, maxWidth + VU_X_MARGIN_LEFT, adaptedY + VU_HEIGHT);

          g2.drawLine(VU_X_MARGIN_LEFT, adaptedY, VU_X_MARGIN_LEFT, adaptedY + VU_HEIGHT);

          MaxLevel[] cacheMaxLevels = maxLevels.get(entry.getKey());
          if (cacheMaxLevels != null) {
            // draw the max levels
            int maxHitX = (int) (cacheMaxLevels[0].getValue() * scaleFactor);
            if (maxHitX < 2) {
              maxHitX = 2;
            }
            g2.setColor(VU_HIT_DRAW_COLOR);
            g2.fillRect(maxHitX - 1 + VU_X_MARGIN_LEFT, adaptedY + 1, 2, VU_HIT_HEIGHT - 1);

            int maxMissX = (int) (cacheMaxLevels[1].getValue() * scaleFactor);
            if (maxMissX < 2) {
              maxMissX = 2;
            }
            g2.setColor(VU_MISS_DRAW_COLOR);
            g2.fillRect(maxMissX - 1 + VU_X_MARGIN_LEFT, adaptedY + 1 + VU_HIT_HEIGHT, 2, VU_MISS_HEIGHT - 1);

            int maxPutX = (int) (cacheMaxLevels[2].getValue() * scaleFactor);
            if (maxPutX < 2) {
              maxPutX = 2;
            }
            g2.setColor(VU_PUT_DRAW_COLOR);
            g2.fillRect(maxPutX - 1 + VU_X_MARGIN_LEFT, adaptedY + 1 + VU_HIT_HEIGHT + VU_MISS_HEIGHT, 2,
                        VU_PUT_HEIGHT - 1);

            // draw the max level values
            g2.setFont(statsFont);

            String maxHitText = String.valueOf(cacheMaxLevels[0].getValue());
            g2.setColor(VU_HIT_DRAW_COLOR);
            g2.drawString(maxHitText, VU_X_MARGIN_LEFT + VU_X_SPACING + maxWidth,
                          1 + adaptedY + VU_HIT_HEIGHT - getVerticalAlignment(VU_HIT_HEIGHT, statsFontMetrics));

            String maxMissText = String.valueOf(cacheMaxLevels[1].getValue());
            g2.setColor(VU_MISS_DRAW_COLOR);
            g2.drawString(maxMissText,
                          VU_X_MARGIN_LEFT + VU_X_SPACING + maxWidth,
                          1 + adaptedY + VU_HIT_HEIGHT + VU_MISS_HEIGHT
                              - getVerticalAlignment(VU_MISS_HEIGHT, statsFontMetrics));

            String maxPutText = String.valueOf(cacheMaxLevels[2].getValue());
            g2.setColor(VU_PUT_DRAW_COLOR);
            g2.drawString(maxPutText, VU_X_MARGIN_LEFT + VU_X_SPACING + maxWidth,
                          1 + adaptedY + VU_HEIGHT - getVerticalAlignment(VU_PUT_HEIGHT, statsFontMetrics));
          }
        }

        int totalVUHeight = y + VU_Y_SPACING - statsFontMetrics.getHeight();
        Dimension preferredSize = getPreferredSize();
        if (preferredSize.getWidth() != getParent().getWidth() || preferredSize.getHeight() != totalVUHeight) {
          Dimension newPreferredSize = new Dimension(getParent().getWidth(), totalVUHeight);
          setPreferredSize(newPreferredSize);
          setMinimumSize(newPreferredSize);
          setMaximumSize(newPreferredSize);
          if (null == cacheSamplesScrollPane) {
            AggregateVuMeterPanel.this.revalidate();
          } else {
            cacheSamplesScrollPane.revalidate();
          }
        }
      }
    }

    public synchronized void setCacheSamples(Map<String, long[]> polledAttribute) {
      previousCacheRenders = currentCacheRenders;
      currentCacheSamples = new TreeMap<String, long[]>(polledAttribute);
      currentCacheRenders = null;
      lastCacheSamplesUpdate = System.currentTimeMillis();
    }
  }

  public void attributesPolled(PolledAttributesResult result) {
    // Aggregate the cache samples from all the clients into one set of stats.
    // If global mode is active, all the caches will be aggregated into one global cache.
    Map<String, long[]> aggregated = new HashMap<String, long[]>();

    for (IClient client : clusterModel.getClients()) {
      ObjectName on = client.getTunneledBeanName(tmplName);

      Map<String, long[]> clientSamples = (Map<String, long[]>) result.getPolledAttribute(client, on,
                                                                                          CACHE_METRICS_ATTR);
      if (clientSamples == null) {
        continue;
      }

      for (Map.Entry<String, long[]> entry : clientSamples.entrySet()) {
        String cacheName;
        if (globalMode) {
          cacheName = GLOBAL_MODE_CACHE_NAME;
        } else {
          cacheName = entry.getKey();
        }
        if (!cacheName.endsWith("org.hibernate.cache.UpdateTimestampsCache")) {
          long[] currentSamples = entry.getValue();
          long[] existingSamples = aggregated.get(cacheName);
          if (null == existingSamples) {
            existingSamples = new long[currentSamples.length];
            System.arraycopy(currentSamples, 0, existingSamples, 0, currentSamples.length);
            aggregated.put(cacheName, existingSamples);
          } else {
            for (int i = 0; i < currentSamples.length; i++) {
              existingSamples[i] += currentSamples[i];
            }
          }
        }
      }
    }

    cacheSamplesPanel.setCacheSamples(aggregated);

    repaintCacheSamplesPanel();
  }

  @Override
  public void tearDown() {
    removePolledAttributeListener();
    IServer activeCoord = clusterModel.getActiveCoordinator();
    if (activeCoord != null) {
      activeCoord.removeClientConnectionListener(this);
    }
    clusterModel.removePropertyChangeListener(clusterListener);
    clusterListener.tearDown();

    super.tearDown();
  }
}