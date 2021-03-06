/*******************************************************************************
 * Copyright (c) 2017 Pegasystems Inc. All rights reserved.
 *
 * Contributors:
 *     Manu Varghese
 *******************************************************************************/
package com.pega.gcs.logviewer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.BevelBorder;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;

import com.pega.gcs.fringecommon.guiutilities.BaseFrame;
import com.pega.gcs.fringecommon.guiutilities.GoToLineDialog;
import com.pega.gcs.fringecommon.guiutilities.MemoryStatusBarJPanel;
import com.pega.gcs.fringecommon.guiutilities.Message;
import com.pega.gcs.fringecommon.guiutilities.Message.MessageType;
import com.pega.gcs.fringecommon.guiutilities.ModalProgressMonitor;
import com.pega.gcs.fringecommon.guiutilities.NavigationTableController;
import com.pega.gcs.fringecommon.guiutilities.RecentFile;
import com.pega.gcs.fringecommon.guiutilities.RecentFileContainer;
import com.pega.gcs.fringecommon.guiutilities.bookmark.BookmarkContainer;
import com.pega.gcs.fringecommon.guiutilities.bookmark.BookmarkModel;
import com.pega.gcs.fringecommon.guiutilities.markerbar.MarkerBar;
import com.pega.gcs.fringecommon.guiutilities.search.SearchData;
import com.pega.gcs.fringecommon.guiutilities.search.SearchMarkerModel;
import com.pega.gcs.fringecommon.guiutilities.search.SearchPanel;
import com.pega.gcs.fringecommon.log4j2.Log4j2Helper;
import com.pega.gcs.fringecommon.utilities.GeneralUtilities;
import com.pega.gcs.logviewer.logfile.LogFileType;
import com.pega.gcs.logviewer.logfile.LogFileType.LogType;
import com.pega.gcs.logviewer.logfile.LogPattern;
import com.pega.gcs.logviewer.model.LogEntry;
import com.pega.gcs.logviewer.model.LogEntryModel;
import com.pega.gcs.logviewer.model.LogViewerSetting;
import com.pega.gcs.logviewer.report.AlertSystemReportDialog;
import com.pega.gcs.logviewer.report.Log4jSystemReportDialog;
import com.pega.gcs.logviewer.report.SystemReportDialog;
import com.pega.gcs.logviewer.report.alertpal.AlertPALReportDialog;

public class LogDataMainPanel extends JPanel {

	private static final long serialVersionUID = -836183144753785975L;

	private static final Log4j2Helper LOG = new Log4j2Helper(LogDataMainPanel.class);

	private static final String SHOW_CHART = "Show Chart";

	private static final String HIDE_CHART = "Hide Chart";

	private RecentFile recentFile;

	private LogViewerSetting logViewerSetting;

	private LogTableModel logTableModel;

	private LogTable logTable;

	private LogTableMouseListener logTableMouseListener;

	private LogFileLoadTask logFileLoadTask;

	private SearchPanel<Integer> searchPanel;

	private JButton chartJButton;

	private JButton gotoLineJButton;

	private JButton overviewJButton;

	private JButton reloadJButton;

	private JButton palOverviewJButton;

	private JButton logXMLExportJButton;

	private JLabel charsetJLabel;

	private JLabel timezoneJLabel;

	private JLabel localeJLabel;

	private JLabel fileSizeJLabel;

	private JTextField statusBar;

	private JProgressBar progressBar;

	private JLabel progressText;

	private JButton stopTailLogFileJButton;

	private NavigationTableController<Integer> navigationTableController;

	private int dividerLocation;

	private JSplitPane chartAndTableSplitPane;

	private ChartAndLegendPanel chartAndLegendPanel;

	private SystemReportDialog systemReportDialog;

	private AlertPALReportDialog alertPALReportDialog;

	@SuppressWarnings("unchecked")
	public LogDataMainPanel(File selectedFile, RecentFileContainer recentFileContainer,
			LogViewerSetting logViewerSetting) {

		super();

		this.logViewerSetting = logViewerSetting;
		this.logFileLoadTask = null;

		String charset = logViewerSetting.getCharset();
		Locale locale = logViewerSetting.getLocale();

		Map<String, Object> defaultAttribsIfNew = new HashMap<>();
		defaultAttribsIfNew.put(RecentFile.KEY_LOCALE, locale);

		this.recentFile = recentFileContainer.getRecentFile(selectedFile, charset, defaultAttribsIfNew);

		SearchData<Integer> searchData = new SearchData<>(null);

		this.logTableModel = new LogTableModel(recentFile, searchData);

		BookmarkContainer<Integer> bookmarkContainer;
		bookmarkContainer = (BookmarkContainer<Integer>) recentFile.getAttribute(RecentFile.KEY_BOOKMARK);

		if (bookmarkContainer == null) {

			bookmarkContainer = new BookmarkContainer<Integer>();

			recentFile.setAttribute(RecentFile.KEY_BOOKMARK, bookmarkContainer);
		}

		BookmarkModel<Integer> bookmarkModel = new BookmarkModel<Integer>(bookmarkContainer, logTableModel);

		logTableModel.setBookmarkModel(bookmarkModel);

		navigationTableController = new NavigationTableController<Integer>(logTableModel);

		logTableModel.addPropertyChangeListener(new PropertyChangeListener() {

			@Override
			public void propertyChange(PropertyChangeEvent evt) {

				String propertyName = evt.getPropertyName();

				if ("message".equals(propertyName)) {

					JTextField statusBar = getStatusBar();
					Message message = (Message) evt.getNewValue();
					setMessage(statusBar, message);
				} else if ("logEntryModel".equals(propertyName)) {
					// 'logEntryModel' fired by LogTableModel as the type
					// of log file is known after parsing the file
					LogTable logTable = getLogTable();
					logTable.updateLogTableColumnModel();
				}

			}
		});

		logTableMouseListener = new LogTableMouseListener(this);

		logTableModel.addTableModelListener(new TableModelListener() {

			@Override
			public void tableChanged(TableModelEvent aE) {
				updateDisplayJPanel();
			}
		});

		setLayout(new GridBagLayout());

		GridBagConstraints gbc1 = new GridBagConstraints();
		gbc1.gridx = 0;
		gbc1.gridy = 0;
		gbc1.weightx = 1.0D;
		gbc1.weighty = 0.0D;
		gbc1.fill = GridBagConstraints.BOTH;
		gbc1.anchor = GridBagConstraints.NORTHWEST;
		gbc1.insets = new Insets(0, 0, 0, 0);

		GridBagConstraints gbc2 = new GridBagConstraints();
		gbc2.gridx = 0;
		gbc2.gridy = 1;
		gbc2.weightx = 1.0D;
		gbc2.weighty = 1.0D;
		gbc2.fill = GridBagConstraints.BOTH;
		gbc2.anchor = GridBagConstraints.NORTHWEST;
		gbc2.insets = new Insets(0, 0, 0, 0);

		GridBagConstraints gbc3 = new GridBagConstraints();
		gbc3.gridx = 0;
		gbc3.gridy = 2;
		gbc3.weightx = 1.0D;
		gbc3.weighty = 0.0D;
		gbc3.fill = GridBagConstraints.HORIZONTAL;
		gbc3.anchor = GridBagConstraints.NORTHWEST;
		gbc3.insets = new Insets(0, 0, 0, 0);

		JPanel utilityPanel = getUtilityPanel();
		JSplitPane chartAndTableSplitPane = getChartAndTableSplitPane();
		JPanel bottomJPanel = getBottomJPanel();

		add(utilityPanel, gbc1);
		add(chartAndTableSplitPane, gbc2);
		add(bottomJPanel, gbc3);

		dividerLocation = chartAndTableSplitPane.getDividerLocation();

		loadFile(this, logTableModel, logViewerSetting, false);

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see javax.swing.JComponent#removeNotify()
	 */
	@Override
	public void removeNotify() {
		super.removeNotify();

		if ((logFileLoadTask != null) && ((!logFileLoadTask.isCancelled()) || (!logFileLoadTask.isDone()))) {
			LOG.info("Tab removed. Cancelling LogFileLoadTask");
			logFileLoadTask.cancel(true);
		}

		clearJDialogList();
	}

	protected LogViewerSetting getLogViewerSetting() {
		return logViewerSetting;
	}

	protected NavigationTableController<Integer> getNavigationTableController() {
		return navigationTableController;
	}

	protected int getDividerLocation() {
		return dividerLocation;
	}

	protected void setDividerLocation(int aDividerLocation) {
		dividerLocation = aDividerLocation;
	}

	protected SystemReportDialog getSystemReportDialog() {

		if (systemReportDialog == null) {

			LogTable logTable = getLogTable();
			LogTableModel logTableModel = (LogTableModel) logTable.getModel();

			LogFileType logFileType = logTableModel.getLogFileType();
			LogType logType = logFileType.getLogType();

			NavigationTableController<Integer> navigationTableController = getNavigationTableController();

			if (logType == LogType.PEGA_ALERT) {
				systemReportDialog = new AlertSystemReportDialog(logTableModel, navigationTableController, logTable,
						BaseFrame.getAppIcon(), LogDataMainPanel.this);
			} else {

				systemReportDialog = new Log4jSystemReportDialog(logTableModel, navigationTableController,
						BaseFrame.getAppIcon(), LogDataMainPanel.this);
			}

			systemReportDialog.addWindowListener(new WindowAdapter() {

				@Override
				public void windowClosed(WindowEvent e) {
					setSystemReportDialog(null);
				}

			});

			systemReportDialog.setVisible(true);
		}

		return systemReportDialog;
	}

	protected void setSystemReportDialog(SystemReportDialog aSystemReportDialog) {
		systemReportDialog = aSystemReportDialog;
	}

	protected AlertPALReportDialog getAlertPALReportDialog() {
		return alertPALReportDialog;
	}

	protected void setAlertPALReportDialog(AlertPALReportDialog aAlertPALReportDialog) {
		alertPALReportDialog = aAlertPALReportDialog;
	}

	protected LogTable getLogTable() {

		if (logTable == null) {
			logTable = new LogTable(logTableModel);
			logTable.setFillsViewportHeight(true);

			navigationTableController.addCustomJTable(logTable);
			logTable.addMouseListener(logTableMouseListener);
		}

		return logTable;
	}

	private SearchPanel<Integer> getSearchPanel() {

		if (searchPanel == null) {

			searchPanel = new SearchPanel<Integer>(navigationTableController, logTableModel.getSearchModel());
		}

		return searchPanel;
	}

	protected JTextField getStatusBar() {

		if (statusBar == null) {
			statusBar = new JTextField();
			statusBar.setEditable(false);
			statusBar.setBackground(null);
			statusBar.setBorder(null);
		}

		return statusBar;
	}

	protected void setMessage(JTextField statusBar, Message message) {

		if (message != null) {

			Color color = Color.BLUE;

			if (message.getMessageType().equals(Message.MessageType.ERROR)) {
				color = Color.RED;
			}

			String text = message.getText();

			statusBar.setForeground(color);
			statusBar.setText(text);
		}
	}

	protected JSplitPane getChartAndTableSplitPane() {

		if (chartAndTableSplitPane == null) {

			ChartAndLegendPanel chartAndLegendPanel = getChartAndLegendPanel();
			JPanel logTablePanel = getLogTablePanel();

			chartAndTableSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, chartAndLegendPanel, logTablePanel);

			chartAndTableSplitPane.setContinuousLayout(true);
		}

		return chartAndTableSplitPane;
	}

	private JPanel getBottomJPanel() {

		JPanel bottomJPanel = new JPanel();

		LayoutManager layout = new BoxLayout(bottomJPanel, BoxLayout.LINE_AXIS);
		bottomJPanel.setLayout(layout);

		JLabel statusLabel = new JLabel("Status:");

		JTextField statusBar = getStatusBar();
		JProgressBar progressBar = getProgressBar();
		JButton stopTailLogFileJButton = getStopTailLogFileJButton();
		JLabel progressText = getProgressText();

		MemoryStatusBarJPanel memoryStatusBarJPanel = new MemoryStatusBarJPanel();

		bottomJPanel.add(Box.createRigidArea(new Dimension(5, 20)));
		bottomJPanel.add(statusLabel);
		bottomJPanel.add(Box.createRigidArea(new Dimension(5, 20)));
		bottomJPanel.add(statusBar);
		bottomJPanel.add(Box.createRigidArea(new Dimension(5, 20)));
		bottomJPanel.add(progressBar);
		bottomJPanel.add(Box.createRigidArea(new Dimension(10, 20)));
		bottomJPanel.add(stopTailLogFileJButton);
		bottomJPanel.add(Box.createRigidArea(new Dimension(10, 20)));
		bottomJPanel.add(progressText);
		bottomJPanel.add(Box.createRigidArea(new Dimension(5, 20)));
		bottomJPanel.add(memoryStatusBarJPanel);

		bottomJPanel.setBorder(new BevelBorder(BevelBorder.LOWERED));

		return bottomJPanel;
	}

	protected ChartAndLegendPanel getChartAndLegendPanel() {

		if (chartAndLegendPanel == null) {

			SearchPanel<Integer> searchPanel = getSearchPanel();

			LogTable logTable = getLogTable();

			chartAndLegendPanel = new ChartAndLegendPanel(logTableModel.getModelName(), logTable, searchPanel);

			chartAndLegendPanel.setVisible(false);

			logTableModel.addTableModelListener(chartAndLegendPanel);
		}

		return chartAndLegendPanel;
	}

	private JPanel getLogTablePanel() {

		JPanel logTablePanel = new JPanel();
		logTablePanel.setLayout(new BorderLayout());

		LogTable logTable = getLogTable();

		JScrollPane logTableScrollpane = new JScrollPane(logTable, ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
				ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		JPanel markerBarPanel = getMarkerBarPanel();

		logTablePanel.add(logTableScrollpane, BorderLayout.CENTER);
		logTablePanel.add(markerBarPanel, BorderLayout.EAST);

		return logTablePanel;
	}

	private JPanel getMarkerBarPanel() {

		JPanel markerBarPanel = new JPanel();
		markerBarPanel.setLayout(new BorderLayout());

		Dimension topDimension = new Dimension(16, 30);

		JLabel topSpacer = new JLabel();
		topSpacer.setPreferredSize(topDimension);

		Dimension bottomDimension = new Dimension(16, 18);

		JLabel bottomSpacer = new JLabel();
		bottomSpacer.setPreferredSize(bottomDimension);

		MarkerBar<Integer> markerBar = getMarkerBar();

		markerBarPanel.add(topSpacer, BorderLayout.NORTH);
		markerBarPanel.add(markerBar, BorderLayout.CENTER);
		markerBarPanel.add(bottomSpacer, BorderLayout.SOUTH);

		return markerBarPanel;
	}

	private MarkerBar<Integer> getMarkerBar() {

		SearchMarkerModel<Integer> searchMarkerModel = new SearchMarkerModel<Integer>(logTableModel);

		MarkerBar<Integer> markerBar = new MarkerBar<Integer>(navigationTableController, searchMarkerModel);

		BookmarkModel<Integer> bookmarkModel;
		bookmarkModel = logTableModel.getBookmarkModel();

		markerBar.addMarkerModel(bookmarkModel);

		return markerBar;

	}

	private JPanel getUtilityPanel() {

		JPanel utilityPanel = new JPanel();
		LayoutManager layout = new BoxLayout(utilityPanel, BoxLayout.X_AXIS);
		utilityPanel.setLayout(layout);

		JPanel searchPanel = getSearchPanel();
		JPanel chartJButtonPanel = getChartJButtonPanel();
		JPanel displayJPanel = getDisplayJPanel();

		utilityPanel.add(searchPanel);
		utilityPanel.add(chartJButtonPanel);
		utilityPanel.add(displayJPanel);

		return utilityPanel;
	}

	private JPanel getChartJButtonPanel() {
		JPanel chartJButtonPanel = new JPanel();

		LayoutManager layout = new BoxLayout(chartJButtonPanel, BoxLayout.X_AXIS);
		chartJButtonPanel.setLayout(layout);

		JButton chartJButton = getChartJButton();
		JButton gotoLineJButton = getGotoLineJButton();
		JButton overviewJButton = getOverviewJButton();
		JButton reloadJButton = getReloadJButton();
		JButton palOverviewJButton = getPalOverviewJButton();
		JButton logXMLExportJButton = getLogXMLExportJButton();

		Dimension spacer = new Dimension(15, 30);
		Dimension endSpacer = new Dimension(10, 30);

		chartJButtonPanel.add(Box.createRigidArea(endSpacer));
		chartJButtonPanel.add(chartJButton);
		chartJButtonPanel.add(Box.createRigidArea(spacer));
		chartJButtonPanel.add(gotoLineJButton);
		chartJButtonPanel.add(Box.createRigidArea(spacer));
		chartJButtonPanel.add(overviewJButton);
		chartJButtonPanel.add(Box.createRigidArea(spacer));
		chartJButtonPanel.add(reloadJButton);
		chartJButtonPanel.add(Box.createRigidArea(spacer));
		chartJButtonPanel.add(palOverviewJButton);
		chartJButtonPanel.add(Box.createRigidArea(spacer));
		chartJButtonPanel.add(logXMLExportJButton);
		chartJButtonPanel.add(Box.createRigidArea(spacer));

		chartJButtonPanel.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1));

		return chartJButtonPanel;
	}

	private JPanel getDisplayJPanel() {

		JPanel displayJPanel = new JPanel();

		LayoutManager layout = new BoxLayout(displayJPanel, BoxLayout.X_AXIS);
		displayJPanel.setLayout(layout);

		Dimension preferredSize = new Dimension(150, 30);
		displayJPanel.setPreferredSize(preferredSize);

		JPanel charsetJPanel = getCharsetJPanel();
		JPanel localeJPanel = getLocaleJPanel();
		JPanel timezoneJPanel = getTimezoneJPanel();
		JPanel fileSizeJPanel = getFileSizeJPanel();

		displayJPanel.add(charsetJPanel);
		displayJPanel.add(localeJPanel);
		displayJPanel.add(timezoneJPanel);
		displayJPanel.add(fileSizeJPanel);

		return displayJPanel;
	}

	protected JButton getChartJButton() {

		if (chartJButton == null) {
			chartJButton = new JButton(SHOW_CHART);

			Dimension size = new Dimension(90, 20);
			chartJButton.setPreferredSize(size);
			chartJButton.setMaximumSize(size);
			chartJButton.setHorizontalTextPosition(SwingConstants.LEADING);

			chartJButton.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(ActionEvent e) {

					JSplitPane chartAndTableSplitPane = getChartAndTableSplitPane();
					ChartAndLegendPanel chartAndLegendPanel = getChartAndLegendPanel();
					JButton chartJButton = getChartJButton();

					if (e.getActionCommand().equals(SHOW_CHART)) {
						chartJButton.setText(HIDE_CHART);
						chartAndLegendPanel.setVisible(true);

						chartAndTableSplitPane.setDividerLocation(getDividerLocation());

					} else {
						chartJButton.setText(SHOW_CHART);

						setDividerLocation(chartAndTableSplitPane.getDividerLocation());
						chartAndLegendPanel.setVisible(false);

					}

				}
			});
		}

		return chartJButton;
	}

	private JButton getGotoLineJButton() {

		if (gotoLineJButton == null) {
			gotoLineJButton = new JButton("Go to line");

			Dimension size = new Dimension(90, 20);
			gotoLineJButton.setPreferredSize(size);
			gotoLineJButton.setMaximumSize(size);
			gotoLineJButton.setHorizontalTextPosition(SwingConstants.LEADING);

			gotoLineJButton.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(ActionEvent e) {

					LogTable logTable = getLogTable();
					LogTableModel logTableModel = (LogTableModel) logTable.getModel();

					LogEntryModel logEntryModel = logTableModel.getLogEntryModel();

					Map<Integer, LogEntry> logEntryMap = logEntryModel.getLogEntryMap();

					List<Integer> logEntryIndexList = new LinkedList<Integer>(logEntryMap.keySet());

					Collections.sort(logEntryIndexList);

					int startIndex = logEntryIndexList.get(0);
					int endIndex = logEntryIndexList.get(logEntryIndexList.size() - 1);

					GoToLineDialog lgtld = new GoToLineDialog(startIndex, endIndex, BaseFrame.getAppIcon(),
							LogDataMainPanel.this);
					lgtld.setVisible(true);

					Integer selectedIndex = lgtld.getSelectedInteger();

					if (selectedIndex != null) {

						getNavigationTableController().scrollToKey(selectedIndex);
					}
				}
			});
		}

		return gotoLineJButton;
	}

	private JButton getOverviewJButton() {

		if (overviewJButton == null) {
			overviewJButton = new JButton("Overview");

			Dimension size = new Dimension(90, 20);
			overviewJButton.setPreferredSize(size);
			overviewJButton.setMaximumSize(size);
			overviewJButton.setHorizontalTextPosition(SwingConstants.LEADING);

			overviewJButton.setEnabled(true);

			overviewJButton.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(ActionEvent e) {

					SystemReportDialog systemReportDialog = getSystemReportDialog();

					systemReportDialog.toFront();

				}
			});
		}

		return overviewJButton;

	}

	private JButton getReloadJButton() {

		if (reloadJButton == null) {
			reloadJButton = new JButton("Reload file");

			Dimension size = new Dimension(90, 20);
			reloadJButton.setPreferredSize(size);
			reloadJButton.setMaximumSize(size);
			// reloadJButton.setHorizontalTextPosition(SwingConstants.LEADING);
			reloadJButton.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(ActionEvent e) {

					LogTable logTable = getLogTable();
					LogTableModel logTableModel = (LogTableModel) logTable.getModel();

					String origCharset = logTableModel.getCharset();
					Locale origLocale = logTableModel.getLocale();
					TimeZone origTimezone = logTableModel.getLogTimeZone();

					ChartTablePanelSettingDialog chartTablePanelSettingDialog;

					chartTablePanelSettingDialog = new ChartTablePanelSettingDialog(origCharset, origLocale,
							origTimezone, BaseFrame.getAppIcon(), LogDataMainPanel.this);

					chartTablePanelSettingDialog.setVisible(true);

					boolean settingUpdated = chartTablePanelSettingDialog.isSettingUpdated();

					if (settingUpdated) {

						String charset = chartTablePanelSettingDialog.getSelectedCharset();
						Locale locale = chartTablePanelSettingDialog.getSelectedLocale();
						TimeZone timezone = chartTablePanelSettingDialog.getSelectedTimeZone();

						logTableModel.updateRecentFile(charset, locale, timezone);

						if (origCharset.equals(charset)) {

							LogTableModel ltm = (LogTableModel) logTable.getModel();

							LogEntryModel lem = ltm.getLogEntryModel();

							if (origLocale != locale) {
								lem.setLocale(locale);
							}

							if (origTimezone != timezone) {
								lem.setDisplayDateFormatTimeZone(timezone);
							}

							ltm.fireTableDataChanged();

							populateDisplayJPanel();

						} else {
							// clear and reset the model.
							logTableModel.resetModel();

							// charset changed, read/parse the file again
							loadFile(LogDataMainPanel.this, logTableModel, getLogViewerSetting(), false);
						}
					}

				}
			});
		}

		return reloadJButton;
	}

	private JButton getPalOverviewJButton() {

		if (palOverviewJButton == null) {

			palOverviewJButton = new JButton("PAL Report");

			Dimension size = new Dimension(90, 20);
			palOverviewJButton.setPreferredSize(size);
			palOverviewJButton.setMaximumSize(size);
			palOverviewJButton.setHorizontalTextPosition(SwingConstants.LEADING);

			palOverviewJButton.setEnabled(false);

			palOverviewJButton.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(ActionEvent e) {

					AlertPALReportDialog alertPALReportDialog = getAlertPALReportDialog();

					if (alertPALReportDialog == null) {
						// additional check to make sure this dialog get called
						// only for alerts
						LogTable logTable = getLogTable();
						LogTableModel logTableModel = (LogTableModel) logTable.getModel();

						LogFileType logFileType = logTableModel.getLogFileType();
						LogType logType = logFileType.getLogType();

						if (logType == LogType.PEGA_ALERT) {

							alertPALReportDialog = new AlertPALReportDialog(logTableModel,
									getNavigationTableController(), BaseFrame.getAppIcon(), LogDataMainPanel.this);

							alertPALReportDialog.setVisible(true);

							alertPALReportDialog.addWindowListener(new WindowAdapter() {

								@Override
								public void windowClosed(WindowEvent e) {
									AlertPALReportDialog alertPALReportDialog = getAlertPALReportDialog();
									alertPALReportDialog.destroyJPanel();
									setAlertPALReportDialog(null);
								}

							});

							setAlertPALReportDialog(alertPALReportDialog);
						}

					} else {
						alertPALReportDialog.toFront();
					}

				}
			});
		}

		return palOverviewJButton;
	}

	private JButton getLogXMLExportJButton() {

		if (logXMLExportJButton == null) {
			logXMLExportJButton = new JButton("Export XML");

			Dimension size = new Dimension(90, 20);
			logXMLExportJButton.setPreferredSize(size);
			logXMLExportJButton.setMaximumSize(size);
			logXMLExportJButton.setHorizontalTextPosition(SwingConstants.LEADING);

			logXMLExportJButton.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(ActionEvent e) {

					LogTable logTable = getLogTable();
					LogTableModel logTableModel = (LogTableModel) logTable.getModel();

					LogXMLExportDialog lxmled = new LogXMLExportDialog(logTableModel, BaseFrame.getAppIcon(),
							LogDataMainPanel.this);
					lxmled.setVisible(true);
				}
			});
		}

		return logXMLExportJButton;
	}

	private JLabel getCharsetJLabel() {

		if (charsetJLabel == null) {
			charsetJLabel = new JLabel();
		}

		return charsetJLabel;
	}

	private JLabel getTimezoneJLabel() {

		if (timezoneJLabel == null) {
			timezoneJLabel = new JLabel();
		}

		return timezoneJLabel;
	}

	private JLabel getLocaleJLabel() {

		if (localeJLabel == null) {
			localeJLabel = new JLabel();
		}

		return localeJLabel;
	}

	private JLabel getFileSizeJLabel() {

		if (fileSizeJLabel == null) {
			fileSizeJLabel = new JLabel();
		}

		return fileSizeJLabel;
	}

	private JPanel getCharsetJPanel() {

		JPanel charsetJPanel = new JPanel();

		LayoutManager layout = new BoxLayout(charsetJPanel, BoxLayout.X_AXIS);
		charsetJPanel.setLayout(layout);

		JLabel charsetJLabel = getCharsetJLabel();

		Dimension dim = new Dimension(1, 30);

		charsetJPanel.add(Box.createHorizontalGlue());
		charsetJPanel.add(Box.createRigidArea(dim));
		charsetJPanel.add(charsetJLabel);
		charsetJPanel.add(Box.createHorizontalGlue());

		charsetJPanel.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1));

		return charsetJPanel;
	}

	private JPanel getTimezoneJPanel() {

		JPanel timezoneJPanel = new JPanel();

		LayoutManager layout = new BoxLayout(timezoneJPanel, BoxLayout.X_AXIS);
		timezoneJPanel.setLayout(layout);

		JLabel timezoneJLabel = getTimezoneJLabel();

		Dimension dim = new Dimension(1, 30);

		timezoneJPanel.add(Box.createHorizontalGlue());
		timezoneJPanel.add(Box.createRigidArea(dim));
		timezoneJPanel.add(timezoneJLabel);
		timezoneJPanel.add(Box.createHorizontalGlue());

		timezoneJPanel.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1));

		return timezoneJPanel;
	}

	private JPanel getLocaleJPanel() {
		JPanel localeJPanel = new JPanel();

		LayoutManager layout = new BoxLayout(localeJPanel, BoxLayout.X_AXIS);
		localeJPanel.setLayout(layout);

		JLabel localeJLabel = getLocaleJLabel();

		Dimension dim = new Dimension(1, 30);

		localeJPanel.add(Box.createHorizontalGlue());
		localeJPanel.add(Box.createRigidArea(dim));
		localeJPanel.add(localeJLabel);
		localeJPanel.add(Box.createHorizontalGlue());

		localeJPanel.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1));

		return localeJPanel;
	}

	private JPanel getFileSizeJPanel() {
		JPanel fileSizeJPanel = new JPanel();

		LayoutManager layout = new BoxLayout(fileSizeJPanel, BoxLayout.X_AXIS);
		fileSizeJPanel.setLayout(layout);

		JLabel fileSizeJLabel = getFileSizeJLabel();

		Dimension dim = new Dimension(1, 30);

		fileSizeJPanel.add(Box.createHorizontalGlue());
		fileSizeJPanel.add(Box.createRigidArea(dim));
		fileSizeJPanel.add(fileSizeJLabel);
		fileSizeJPanel.add(Box.createHorizontalGlue());

		fileSizeJPanel.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1));

		return fileSizeJPanel;
	}

	protected JProgressBar getProgressBar() {

		if (progressBar == null) {
			progressBar = new JProgressBar();

			progressBar.setStringPainted(true);
		}

		return progressBar;
	}

	protected JLabel getProgressText() {

		if (progressText == null) {
			progressText = new JLabel();

			Dimension size = new Dimension(200, 20);
			progressText.setPreferredSize(size);
			progressText.setMaximumSize(size);
		}

		return progressText;
	}

	protected JButton getStopTailLogFileJButton() {

		if (stopTailLogFileJButton == null) {
			stopTailLogFileJButton = new JButton("Stop tailing");

			LogViewerSetting logViewerSetting = getLogViewerSetting();

			if (logViewerSetting.isTailLogFile()) {
				stopTailLogFileJButton.setEnabled(true);
			} else {
				stopTailLogFileJButton.setEnabled(false);
			}

			stopTailLogFileJButton.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(ActionEvent e) {

					if ((logFileLoadTask != null)
							&& ((!logFileLoadTask.isCancelled()) || (!logFileLoadTask.isDone()))) {

						stopTailLogFileJButton.setEnabled(false);

						logFileLoadTask.stopTailing();

						LOG.info("Stop tailing LogFileLoadTask.");
					}
				}
			});
		}

		return stopTailLogFileJButton;
	}

	protected void updateDisplayJPanel() {

		LOG.info("updateDisplayJPanel");
		populateDisplayJPanel();

		LogTable logTable = getLogTable();
		LogTableModel logTableModel = (LogTableModel) logTable.getModel();

		LogFileType logFileType = logTableModel.getLogFileType();

		if (logFileType != null) {
			LogType logType = logFileType.getLogType();

			if (logType == LogType.PEGA_ALERT) {
				JButton palOverviewJButton;
				palOverviewJButton = getPalOverviewJButton();
				palOverviewJButton.setEnabled(true);
			}
		}
	}

	protected void populateDisplayJPanel() {

		LOG.info("populateDisplayJPanel");
		JLabel charsetJLabel = getCharsetJLabel();
		JLabel timezoneJLabel = getTimezoneJLabel();
		JLabel localeJLabel = getLocaleJLabel();
		JLabel fileSizeJLabel = getFileSizeJLabel();

		LogTable logTable = getLogTable();
		LogTableModel logTableModel = (LogTableModel) logTable.getModel();

		String charset = logTableModel.getCharset();
		Locale locale = logTableModel.getLocale();
		TimeZone timeZone = logTableModel.getLogTimeZone();
		Long fileSize = logTableModel.getFileSize();

		String timezoneStr = null;

		if (timeZone != null) {
			timezoneStr = timeZone.getDisplayName(timeZone.useDaylightTime(), TimeZone.SHORT);
		}

		String fileSizeStr = null;

		if (fileSize != null) {
			fileSizeStr = GeneralUtilities.humanReadableSize(fileSize.longValue(), false);
		}

		charsetJLabel.setText(charset);
		localeJLabel.setText(locale.toString());
		timezoneJLabel.setText(timezoneStr);
		fileSizeJLabel.setText(fileSizeStr);

	}

	protected void loadFile(final JComponent parent, final LogTableModel logTableModel,
			LogViewerSetting logViewerSetting, final boolean waitMode) {

		if ((logFileLoadTask != null) && ((!logFileLoadTask.isCancelled()) || (!logFileLoadTask.isDone()))) {
			logFileLoadTask.cancel(true);
			LOG.info("cancelling previous LogFileLoadTask.");
		}

		RecentFile recentFile = logTableModel.getRecentFile();

		if (recentFile != null) {

			// final String logFilePath = (String)
			// recentFile.getAttribute(RecentFile.KEY_FILE);

			UIManager.put("ModalProgressMonitor.progressText", "Loading log file");

			final ModalProgressMonitor mProgressMonitor = new ModalProgressMonitor(parent, "",
					"Loaded 0 log events (0%)", 0, 100);
			mProgressMonitor.setMillisToDecideToPopup(0);
			mProgressMonitor.setMillisToPopup(0);

			boolean tailLogFile = logViewerSetting.isTailLogFile();
			Set<LogPattern> pegaRuleslog4jPatternSet = logViewerSetting.getPegaRuleslog4jPatternSet();

			JProgressBar progressBar = getProgressBar();
			JLabel progressText = getProgressText();
			JLabel errorText = null;

			logFileLoadTask = new LogFileLoadTask(parent, logTableModel, pegaRuleslog4jPatternSet, tailLogFile,
					mProgressMonitor, progressBar, progressText, errorText) {

				/*
				 * (non-Javadoc)
				 * 
				 * @see javax.swing.SwingWorker#done()
				 */
				@Override
				protected void done() {

					if (!waitMode) {
						completeLoad(this, mProgressMonitor, parent, logTableModel);
					}
				}
			};

			logFileLoadTask.execute();

			if (waitMode) {
				completeLoad(logFileLoadTask, mProgressMonitor, parent, logTableModel);
			}
		} else {
			logTableModel.setMessage(new Message(MessageType.ERROR, "No file selected for model"));
		}
	}

	// because of continuous read, complete load may never occur unless there is
	// some error. hence all the post load actions needs to be triggered using
	// table model from within LogFileLoadTask
	protected void completeLoad(LogFileLoadTask tflt, ModalProgressMonitor mProgressMonitor, JComponent parent,
			LogTableModel logTableModel) {

		String filePath = logTableModel.getFilePath();

		try {

			tflt.get();

			System.gc();

			int processedCount = tflt.getProcessedCount();

			logTableModel.fireTableDataChanged();

			LOG.info("LogFileLoadTask - Done: " + filePath + " processedCount:" + processedCount);

		} catch (CancellationException ce) {

			LOG.error("LogFileLoadTask - Cancelled " + filePath);

			MessageType messageType = MessageType.ERROR;
			Message modelmessage = new Message(messageType, filePath + " - file loading cancelled.");
			logTableModel.setMessage(modelmessage);

		} catch (ExecutionException ee) {

			LOG.error("Execution Error in LogFileLoadTask", ee);

			String message = null;

			if (ee.getCause() instanceof OutOfMemoryError) {

				message = "Out Of Memory Error has occured while loading " + filePath
						+ ".\nPlease increase the JVM's max heap size (-Xmx) and try again.";

				JOptionPane.showMessageDialog(parent, message, "Out Of Memory Error", JOptionPane.ERROR_MESSAGE);
			} else {
				message = ee.getCause().getMessage() + " has occured while loading " + filePath + ".";

				JOptionPane.showMessageDialog(parent, message, "Error", JOptionPane.ERROR_MESSAGE);
			}

			MessageType messageType = MessageType.ERROR;
			Message modelmessage = new Message(messageType, message);
			logTableModel.setMessage(modelmessage);

		} catch (Exception e) {
			LOG.error("Error loading file: " + filePath, e);
			MessageType messageType = MessageType.ERROR;

			StringBuffer messageB = new StringBuffer();
			messageB.append("Error loading file: ");
			messageB.append(filePath);

			Message message = new Message(messageType, messageB.toString());
			logTableModel.setMessage(message);

		} finally {

			mProgressMonitor.close();

			System.gc();
		}
	}

	private void clearJDialogList() {

		// in case of error in file load.
		if (logTableMouseListener != null) {
			logTableMouseListener.clearJDialogList();
		}

		if (systemReportDialog != null) {
			systemReportDialog.dispose();
			systemReportDialog = null;
		}
	}
}
