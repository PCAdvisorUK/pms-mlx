/*
 * PS3 Media Server, for streaming any medias to your PS3.
 * Copyright (C) 2008  A.Brochard
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; version 2
 * of the License only.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package net.pms.encoders;

import static net.pms.formats.v2.AudioUtils.getLPCMChannelMappingForMencoder;
import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.apache.commons.lang3.StringUtils.defaultString;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.startsWith;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import net.pms.Messages;
import net.pms.PMS;
import net.pms.configuration.FormatConfiguration;
import net.pms.configuration.PmsConfiguration;
import net.pms.configuration.RendererConfiguration;
import net.pms.dlna.DLNAMediaAudio;
import net.pms.dlna.DLNAMediaInfo;
import net.pms.dlna.DLNAResource;
import net.pms.dlna.InputFile;
import net.pms.formats.Format;
import net.pms.formats.v2.SubtitleType;
import net.pms.formats.v2.SubtitleUtils;
import net.pms.io.OutputParams;
import net.pms.io.PipeIPCProcess;
import net.pms.io.PipeProcess;
import net.pms.io.ProcessWrapper;
import net.pms.io.ProcessWrapperImpl;
import net.pms.io.StreamModifier;
import net.pms.network.HTTPResource;
import net.pms.util.CodecUtil;
import net.pms.util.FileUtil;
import net.pms.util.FormLayoutUtil;
import net.pms.util.PlayerUtil;
import net.pms.util.ProcessUtil;

import org.apache.commons.configuration.event.ConfigurationEvent;
import org.apache.commons.configuration.event.ConfigurationListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bsh.EvalError;
import bsh.Interpreter;

import com.jgoodies.forms.builder.PanelBuilder;
import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.forms.layout.FormLayout;
import com.sun.jna.Platform;

public class MEncoderVideo extends Player {
	private static final Logger logger = LoggerFactory.getLogger(MEncoderVideo.class);
	private static final String COL_SPEC = "left:pref, 3dlu, p:grow, 3dlu, right:p:grow, 3dlu, p:grow, 3dlu, right:p:grow,3dlu, p:grow, 3dlu, right:p:grow,3dlu, pref:grow";
	private static final String ROW_SPEC = "p, 3dlu, p, 3dlu, p, 3dlu, p, 3dlu, p, 3dlu, p, 3dlu, p, 3dlu, p, 9dlu, p, 3dlu, p, 3dlu, p, 3dlu, p, 3dlu, p, 3dlu, p, 3dlu, p";
	private static final String REMOVE_OPTION = "---REMOVE-ME---"; // use an out-of-band option that can't be confused with a real option

	private JTextField mencoder_noass_scale;
	private JTextField mencoder_noass_subpos;
	private JTextField mencoder_noass_blur;
	private JTextField mencoder_noass_outline;
	private JTextField mencoder_custom_options;
	private JTextField subq;
	private JCheckBox forcefps;
	private JCheckBox yadif;
	private JCheckBox scaler;
	private JTextField scaleX;
	private JTextField scaleY;
	private JCheckBox assdefaultstyle;
	private JCheckBox fc;
	private JCheckBox ass;
	private JCheckBox checkBox;
	private JCheckBox mencodermt;
	private JCheckBox noskip;
	private JCheckBox intelligentsync;
	private JTextField ocw;
	private JTextField och;
	private final PmsConfiguration configuration;

	private static final String[] INVALID_CUSTOM_OPTIONS = {
		"-of",
		"-oac",
		"-ovc",
		"-mpegopts"
	};

	private static final String INVALID_CUSTOM_OPTIONS_LIST = Arrays.toString(INVALID_CUSTOM_OPTIONS);

	public static final String ID = "mencoder";

	// TODO (breaking change): most (probably all) of these
	// protected fields should be private. And at least two
	// shouldn't be fields

	@Deprecated
	protected boolean dvd;

	@Deprecated
	protected String overriddenMainArgs[];

	protected boolean dtsRemux;
	protected boolean pcm;
	protected boolean ovccopy;
	protected boolean ac3Remux;
	protected boolean mpegts;
	protected boolean wmv;

	public static final String DEFAULT_CODEC_CONF_SCRIPT =
		Messages.getString("MEncoderVideo.68")
		+ Messages.getString("MEncoderVideo.69")
		+ Messages.getString("MEncoderVideo.70")
		+ Messages.getString("MEncoderVideo.71")
		+ Messages.getString("MEncoderVideo.72")
		+ Messages.getString("MEncoderVideo.73")
		+ Messages.getString("MEncoderVideo.75")
		+ Messages.getString("MEncoderVideo.76")
		+ Messages.getString("MEncoderVideo.77")
		+ Messages.getString("MEncoderVideo.78")
		+ Messages.getString("MEncoderVideo.79")
		+ "#\n"
		+ Messages.getString("MEncoderVideo.80")
		+ "container == iso :: -nosync\n"
		+ "(container == avi || container == matroska) && vcodec == mpeg4 && acodec == mp3 :: -mc 0.1\n"
		+ "container == flv :: -mc 0.1\n"
		+ "container == mov :: -mc 0.1\n"
		+ "container == rm  :: -mc 0.1\n"
		+ "container == matroska && framerate == 29.97  :: -nomux -mc 0\n"
		+ "container == mp4 && vcodec == h264 :: -mc 0.1\n"
		+ "\n"
		+ Messages.getString("MEncoderVideo.87")
		+ Messages.getString("MEncoderVideo.88")
		+ Messages.getString("MEncoderVideo.89")
		+ Messages.getString("MEncoderVideo.91");

	public JCheckBox getCheckBox() {
		return checkBox;
	}

	public JCheckBox getNoskip() {
		return noskip;
	}

	public MEncoderVideo(PmsConfiguration configuration) {
		this.configuration = configuration;
	}

	@Override
	public JComponent config() {
		// Apply the orientation for the locale
		Locale locale = new Locale(configuration.getLanguage());
		ComponentOrientation orientation = ComponentOrientation.getOrientation(locale);
		String colSpec = FormLayoutUtil.getColSpec(COL_SPEC, orientation);

		FormLayout layout = new FormLayout(colSpec, ROW_SPEC);
		PanelBuilder builder = new PanelBuilder(layout);

		CellConstraints cc = new CellConstraints();

		checkBox = new JCheckBox(Messages.getString("MEncoderVideo.0"));
		checkBox.setContentAreaFilled(false);

		if (configuration.getSkipLoopFilterEnabled()) {
			checkBox.setSelected(true);
		}

		checkBox.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e) {
				configuration.setSkipLoopFilterEnabled((e.getStateChange() == ItemEvent.SELECTED));
			}
		});

		JComponent cmp = builder.addSeparator(Messages.getString("NetworkTab.5"), FormLayoutUtil.flip(cc.xyw(1, 1, 15), colSpec, orientation));
		cmp = (JComponent) cmp.getComponent(0);
		cmp.setFont(cmp.getFont().deriveFont(Font.BOLD));

		mencodermt = new JCheckBox(Messages.getString("MEncoderVideo.35"));
		mencodermt.setContentAreaFilled(false);

		if (configuration.getMencoderMT()) {
			mencodermt.setSelected(true);
		}

		mencodermt.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				configuration.setMencoderMT(mencodermt.isSelected());
			}
		});

		mencodermt.setEnabled(Platform.isWindows() || Platform.isMac());

		builder.add(mencodermt, FormLayoutUtil.flip(cc.xy(1, 3), colSpec, orientation));
		builder.add(checkBox, FormLayoutUtil.flip(cc.xyw(3, 3, 12), colSpec, orientation));

		noskip = new JCheckBox(Messages.getString("MEncoderVideo.2"));
		noskip.setContentAreaFilled(false);

		if (configuration.isMencoderNoOutOfSync()) {
			noskip.setSelected(true);
		}

		noskip.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e) {
				configuration.setMencoderNoOutOfSync((e.getStateChange() == ItemEvent.SELECTED));
			}
		});

		builder.add(noskip, FormLayoutUtil.flip(cc.xy(1, 5), colSpec, orientation));

		JButton button = new JButton(Messages.getString("MEncoderVideo.29"));
		button.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				JPanel codecPanel = new JPanel(new BorderLayout());
				final JTextArea textArea = new JTextArea();
				textArea.setText(configuration.getMencoderCodecSpecificConfig());
				textArea.setFont(new Font("Courier", Font.PLAIN, 12));
				JScrollPane scrollPane = new JScrollPane(textArea);
				scrollPane.setPreferredSize(new java.awt.Dimension(900, 100));

				final JTextArea textAreaDefault = new JTextArea();
				textAreaDefault.setText(DEFAULT_CODEC_CONF_SCRIPT);
				textAreaDefault.setBackground(Color.WHITE);
				textAreaDefault.setFont(new Font("Courier", Font.PLAIN, 12));
				textAreaDefault.setEditable(false);
				textAreaDefault.setEnabled(configuration.isMencoderIntelligentSync());
				JScrollPane scrollPaneDefault = new JScrollPane(textAreaDefault);
				scrollPaneDefault.setPreferredSize(new java.awt.Dimension(900, 450));

				JPanel customPanel = new JPanel(new BorderLayout());
				intelligentsync = new JCheckBox(Messages.getString("MEncoderVideo.3"));
				intelligentsync.setContentAreaFilled(false);

				if (configuration.isMencoderIntelligentSync()) {
					intelligentsync.setSelected(true);
				}

				intelligentsync.addItemListener(new ItemListener() {
					@Override
					public void itemStateChanged(ItemEvent e) {
						configuration.setMencoderIntelligentSync((e.getStateChange() == ItemEvent.SELECTED));
						textAreaDefault.setEnabled(configuration.isMencoderIntelligentSync());

					}
				});

				JLabel label = new JLabel(Messages.getString("MEncoderVideo.33"));
				customPanel.add(label, BorderLayout.NORTH);
				customPanel.add(scrollPane, BorderLayout.SOUTH);

				codecPanel.add(intelligentsync, BorderLayout.NORTH);
				codecPanel.add(scrollPaneDefault, BorderLayout.CENTER);
				codecPanel.add(customPanel, BorderLayout.SOUTH);

				while (JOptionPane.showOptionDialog(SwingUtilities.getWindowAncestor((Component) PMS.get().getFrame()),
					codecPanel, Messages.getString("MEncoderVideo.34"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE, null, null, null) == JOptionPane.OK_OPTION) {
					String newCodecparam = textArea.getText();
					DLNAMediaInfo fakemedia = new DLNAMediaInfo();
					DLNAMediaAudio audio = new DLNAMediaAudio();
					audio.setCodecA("ac3");
					fakemedia.setCodecV("mpeg4");
					fakemedia.setContainer("matroska");
					fakemedia.setDuration(45d*60);
					audio.getAudioProperties().setNumberOfChannels(2);
					fakemedia.setWidth(1280);
					fakemedia.setHeight(720);
					audio.setSampleFrequency("48000");
					fakemedia.setFrameRate("23.976");
					fakemedia.getAudioTracksList().add(audio);
					String result[] = getSpecificCodecOptions(newCodecparam, fakemedia, new OutputParams(configuration), "dummy.mpg", "dummy.srt", false, true);

					if (result.length > 0 && result[0].startsWith("@@")) {
						String errorMessage = result[0].substring(2);
						JOptionPane.showMessageDialog(
							SwingUtilities.getWindowAncestor((Component) PMS.get().getFrame()),
							errorMessage,
							Messages.getString("Dialog.Error"),
							JOptionPane.ERROR_MESSAGE
						);
					} else {
						configuration.setMencoderCodecSpecificConfig(newCodecparam);
						break;
					}
				}
			}
		});
		builder.add(button, FormLayoutUtil.flip(cc.xy(1, 11), colSpec, orientation));

		forcefps = new JCheckBox(Messages.getString("MEncoderVideo.4"));
		forcefps.setContentAreaFilled(false);
		if (configuration.isMencoderForceFps()) {
			forcefps.setSelected(true);
		}
		forcefps.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e) {
				configuration.setMencoderForceFps(e.getStateChange() == ItemEvent.SELECTED);
			}
		});

		builder.add(forcefps, FormLayoutUtil.flip(cc.xyw(1, 7, 2), colSpec, orientation));

		yadif = new JCheckBox(Messages.getString("MEncoderVideo.26"));
		yadif.setContentAreaFilled(false);
		if (configuration.isMencoderYadif()) {
			yadif.setSelected(true);
		}
		yadif.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e) {
				configuration.setMencoderYadif(e.getStateChange() == ItemEvent.SELECTED);
			}
		});
		builder.add(yadif, FormLayoutUtil.flip(cc.xyw(3, 7, 7), colSpec, orientation));

		scaler = new JCheckBox(Messages.getString("MEncoderVideo.27"));
		scaler.setContentAreaFilled(false);
		scaler.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e) {
				configuration.setMencoderScaler(e.getStateChange() == ItemEvent.SELECTED);
				scaleX.setEnabled(configuration.isMencoderScaler());
				scaleY.setEnabled(configuration.isMencoderScaler());
			}
		});
		builder.add(scaler, FormLayoutUtil.flip(cc.xyw(3, 5, 7), colSpec, orientation));

		builder.addLabel(Messages.getString("MEncoderVideo.28"), FormLayoutUtil.flip(cc.xy(9, 5, CellConstraints.RIGHT, CellConstraints.CENTER), colSpec, orientation));
		scaleX = new JTextField("" + configuration.getMencoderScaleX());
		scaleX.addKeyListener(new KeyAdapter() {
			@Override
			public void keyReleased(KeyEvent e) {
				try {
					configuration.setMencoderScaleX(Integer.parseInt(scaleX.getText()));
				} catch (NumberFormatException nfe) {
					logger.debug("Could not parse scaleX from \"" + scaleX.getText() + "\"");
				}
			}
		});
		builder.add(scaleX, FormLayoutUtil.flip(cc.xy(11, 5), colSpec, orientation));

		builder.addLabel(Messages.getString("MEncoderVideo.30"), FormLayoutUtil.flip(cc.xy(13, 5, CellConstraints.RIGHT, CellConstraints.CENTER), colSpec, orientation));
		scaleY = new JTextField("" + configuration.getMencoderScaleY());
		scaleY.addKeyListener(new KeyAdapter() {
			@Override
			public void keyReleased(KeyEvent e) {
				try {
					configuration.setMencoderScaleY(Integer.parseInt(scaleY.getText()));
				} catch (NumberFormatException nfe) {
					logger.debug("Could not parse scaleY from \"" + scaleY.getText() + "\"");
				}
			}
		});
		builder.add(scaleY, FormLayoutUtil.flip(cc.xy(15, 5), colSpec, orientation));

		if (configuration.isMencoderScaler()) {
			scaler.setSelected(true);
		} else {
			scaleX.setEnabled(false);
			scaleY.setEnabled(false);
		}

		builder.addLabel(Messages.getString("MEncoderVideo.6"), FormLayoutUtil.flip(cc.xy(1, 13), colSpec, orientation));
		mencoder_custom_options = new JTextField(configuration.getMencoderCustomOptions());
		mencoder_custom_options.addKeyListener(new KeyAdapter() {
			@Override
			public void keyReleased(KeyEvent e) {
				configuration.setMencoderCustomOptions(mencoder_custom_options.getText());
			}
		});
		builder.add(mencoder_custom_options, FormLayoutUtil.flip(cc.xyw(3, 13, 13), colSpec, orientation));

		builder.addLabel(Messages.getString("MEncoderVideo.93"), FormLayoutUtil.flip(cc.xy(1, 15), colSpec, orientation));

		builder.addLabel(Messages.getString("MEncoderVideo.28") + " (%)", FormLayoutUtil.flip(cc.xy(1, 15, CellConstraints.RIGHT, CellConstraints.CENTER), colSpec, orientation));
		ocw = new JTextField(configuration.getMencoderOverscanCompensationWidth());
		ocw.addKeyListener(new KeyAdapter() {
			@Override
			public void keyReleased(KeyEvent e) {
				configuration.setMencoderOverscanCompensationWidth(ocw.getText());
			}
		});
		builder.add(ocw, FormLayoutUtil.flip(cc.xy(3, 15), colSpec, orientation));

		builder.addLabel(Messages.getString("MEncoderVideo.30") + " (%)", FormLayoutUtil.flip(cc.xy(5, 15), colSpec, orientation));
		och = new JTextField(configuration.getMencoderOverscanCompensationHeight());
		och.addKeyListener(new KeyAdapter() {
			@Override
			public void keyReleased(KeyEvent e) {
				configuration.setMencoderOverscanCompensationHeight(och.getText());
			}
		});
		builder.add(och, FormLayoutUtil.flip(cc.xy(7, 15), colSpec, orientation));

		cmp = builder.addSeparator(Messages.getString("MEncoderVideo.8"), FormLayoutUtil.flip(cc.xyw(1, 17, 15), colSpec, orientation));
		cmp = (JComponent) cmp.getComponent(0);
		cmp.setFont(cmp.getFont().deriveFont(Font.BOLD));

		builder.addLabel(Messages.getString("MEncoderVideo.16"), FormLayoutUtil.flip(cc.xy(1, 27, CellConstraints.RIGHT, CellConstraints.CENTER), colSpec, orientation));

		mencoder_noass_scale = new JTextField(configuration.getMencoderNoAssScale());
		mencoder_noass_scale.addKeyListener(new KeyAdapter() {
			@Override
			public void keyReleased(KeyEvent e) {
				configuration.setMencoderNoAssScale(mencoder_noass_scale.getText());
			}
		});

		builder.addLabel(Messages.getString("MEncoderVideo.17"), FormLayoutUtil.flip(cc.xy(5, 27), colSpec, orientation));

		mencoder_noass_outline = new JTextField(configuration.getMencoderNoAssOutline());
		mencoder_noass_outline.addKeyListener(new KeyAdapter() {
			@Override
			public void keyReleased(KeyEvent e) {
				configuration.setMencoderNoAssOutline(mencoder_noass_outline.getText());
			}
		});

		builder.addLabel(Messages.getString("MEncoderVideo.18"), FormLayoutUtil.flip(cc.xy(9, 27), colSpec, orientation));

		mencoder_noass_blur = new JTextField(configuration.getMencoderNoAssBlur());
		mencoder_noass_blur.addKeyListener(new KeyAdapter() {
			@Override
			public void keyReleased(KeyEvent e) {
				configuration.setMencoderNoAssBlur(mencoder_noass_blur.getText());
			}
		});

		builder.addLabel(Messages.getString("MEncoderVideo.19"), FormLayoutUtil.flip(cc.xy(13, 27), colSpec, orientation));

		mencoder_noass_subpos = new JTextField(configuration.getMencoderNoAssSubPos());
		mencoder_noass_subpos.addKeyListener(new KeyAdapter() {
			@Override
			public void keyReleased(KeyEvent e) {
				configuration.setMencoderNoAssSubPos(mencoder_noass_subpos.getText());
			}
		});

		builder.add(mencoder_noass_scale, FormLayoutUtil.flip(cc.xy(3, 27), colSpec, orientation));
		builder.add(mencoder_noass_outline, FormLayoutUtil.flip(cc.xy(7, 27), colSpec, orientation));
		builder.add(mencoder_noass_blur, FormLayoutUtil.flip(cc.xy(11, 27), colSpec, orientation));
		builder.add(mencoder_noass_subpos, FormLayoutUtil.flip(cc.xy(15, 27), colSpec, orientation));

		ass = new JCheckBox(Messages.getString("MEncoderVideo.20"));
		ass.setContentAreaFilled(false);
		ass.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e) {
				if (e != null) {
					configuration.setMencoderAss(e.getStateChange() == ItemEvent.SELECTED);
				}
			}
		});
		builder.add(ass, FormLayoutUtil.flip(cc.xy(1, 23), colSpec, orientation));
		ass.setSelected(configuration.isMencoderAss());
		ass.getItemListeners()[0].itemStateChanged(null);

		fc = new JCheckBox(Messages.getString("MEncoderVideo.21"));
		fc.setContentAreaFilled(false);
		fc.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e) {
				configuration.setMencoderFontConfig(e.getStateChange() == ItemEvent.SELECTED);
			}
		});
		builder.add(fc, FormLayoutUtil.flip(cc.xyw(3, 23, 5), colSpec, orientation));
		fc.setSelected(configuration.isMencoderFontConfig());

		assdefaultstyle = new JCheckBox(Messages.getString("MEncoderVideo.36"));
		assdefaultstyle.setContentAreaFilled(false);
		assdefaultstyle.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e) {
				configuration.setMencoderAssDefaultStyle(e.getStateChange() == ItemEvent.SELECTED);
			}
		});
		builder.add(assdefaultstyle, FormLayoutUtil.flip(cc.xyw(8, 23, 4), colSpec, orientation));
		assdefaultstyle.setSelected(configuration.isMencoderAssDefaultStyle());

		builder.addLabel(Messages.getString("MEncoderVideo.92"), FormLayoutUtil.flip(cc.xy(1, 29), colSpec, orientation));
		subq = new JTextField(configuration.getMencoderVobsubSubtitleQuality());
		subq.addKeyListener(new KeyAdapter() {
			@Override
			public void keyReleased(KeyEvent e) {
				configuration.setMencoderVobsubSubtitleQuality(subq.getText());
			}
		});
		builder.add(subq, FormLayoutUtil.flip(cc.xyw(3, 29, 1), colSpec, orientation));

		configuration.addConfigurationListener(new ConfigurationListener() {
			@Override
			public void configurationChanged(ConfigurationEvent event) {
				if (event.getPropertyName() == null) {
					return;
				}
				if ((!event.isBeforeUpdate()) && event.getPropertyName().equals(PmsConfiguration.KEY_DISABLE_SUBTITLES)) {
					boolean enabled = !configuration.isDisableSubtitles();
					ass.setEnabled(enabled);
					assdefaultstyle.setEnabled(enabled);
					fc.setEnabled(enabled);
					mencoder_noass_scale.setEnabled(enabled);
					mencoder_noass_outline.setEnabled(enabled);
					mencoder_noass_blur.setEnabled(enabled);
					mencoder_noass_subpos.setEnabled(enabled);
					ocw.setEnabled(enabled);
					och.setEnabled(enabled);
					subq.setEnabled(enabled);

					if (enabled) {
						ass.getItemListeners()[0].itemStateChanged(null);
					}
				}
			}
		});

		JPanel panel = builder.getPanel();

		// Apply the orientation to the panel and all components in it
		panel.applyComponentOrientation(orientation);

		return panel;
	}

	@Override
	public PlayerPurpose getPurpose() {
		return PlayerPurpose.VIDEO_FILE_PLAYER;
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public boolean isTimeSeekable() {
		return true;
	}

	protected String[] getDefaultArgs() {
		List<String> defaultArgsList = new ArrayList<String>();

		defaultArgsList.add("-msglevel");
		defaultArgsList.add("statusline=2");

		defaultArgsList.add("-oac");
		defaultArgsList.add((ac3Remux || dtsRemux) ? "copy" : (pcm ? "pcm" : "lavc"));

		defaultArgsList.add("-of");
		defaultArgsList.add((wmv || mpegts) ? "lavf" : ((pcm && avisynth()) ? "avi" : ((pcm || dtsRemux) ? "rawvideo" : "mpeg")));

		if (wmv) {
			defaultArgsList.add("-lavfopts");
			defaultArgsList.add("format=asf");
		} else if (mpegts) {
			defaultArgsList.add("-lavfopts");
			defaultArgsList.add("format=mpegts");
		}

		defaultArgsList.add("-mpegopts");
		defaultArgsList.add("format=mpeg2:muxrate=500000:vbuf_size=1194:abuf_size=64");


		defaultArgsList.add("-ovc");
		defaultArgsList.add(ovccopy ? "copy" : "lavc");

		String[] defaultArgsArray = new String[defaultArgsList.size()];
		defaultArgsList.toArray(defaultArgsArray);

		return defaultArgsArray;
	}

	/**
	 * Returns the argument string surrounded with quotes if it contains a space,
	 * otherwise returns the string as is.
	 *
	 * @param arg The argument string
	 * @return The string, optionally in quotes. 
	 */
	private String quoteArg(String arg) {
		if (arg == null || arg.indexOf(" ") == -1) {
			return arg;
		}

		StringBuilder result = new StringBuilder();
		result.append("\"").append(arg).append("\"");
		return result.toString();
	}

	private String[] sanitizeArgs(String[] args) {
		List<String> sanitized = new ArrayList<String>();
		int i = 0;

		while (i < args.length) {
			String name = args[i];
			String value = null;

			for (String option : INVALID_CUSTOM_OPTIONS) {
				if (option.equals(name)) {
					if ((i + 1) < args.length) {
					   value = " " + args[i + 1];
					   ++i;
					} else {
					   value = "";
					}

					logger.warn(
						"Ignoring custom MEncoder option: {}{}; the following options cannot be changed: " + INVALID_CUSTOM_OPTIONS_LIST,
						name,
						value
					);

					break;
				}
			}

			if (value == null) {
				sanitized.add(args[i]);
			}

			++i;
		}

		return sanitized.toArray(new String[sanitized.size()]);
	}

	@Override
	public String[] args() {
		String args[];
		String defaultArgs[] = getDefaultArgs();

		if (overriddenMainArgs != null) {
			// add the sanitized custom MEncoder options.
			// not cached because they may be changed on the fly in the GUI
			// TODO if/when we upgrade to org.apache.commons.lang3:
			// args = ArrayUtils.addAll(defaultArgs, sanitizeArgs(overriddenMainArgs))
			String[] sanitizedCustomArgs = sanitizeArgs(overriddenMainArgs);
			args = new String[defaultArgs.length + sanitizedCustomArgs.length];
			System.arraycopy(defaultArgs, 0, args, 0, defaultArgs.length);
			System.arraycopy(sanitizedCustomArgs, 0, args, defaultArgs.length, sanitizedCustomArgs.length);
		} else {
			args = defaultArgs;
		}

		return args;
	}

	@Override
	public String executable() {
		return configuration.getMencoderPath();
	}

	private int[] getVideoBitrateConfig(String bitrate) {
		int bitrates[] = new int[2];

		if (bitrate.contains("(") && bitrate.contains(")")) {
			try {
				bitrates[1] = Integer.parseInt(bitrate.substring(bitrate.indexOf("(") + 1, bitrate.indexOf(")")));
			} catch (NumberFormatException e) {
				bitrates[1] = 0;
			}
		}

		if (bitrate.contains("(")) {
			bitrate = bitrate.substring(0, bitrate.indexOf("(")).trim();
		}

		if (isBlank(bitrate)) {
			bitrate = "0";
		}

		try {
			bitrates[0] = (int) Double.parseDouble(bitrate);
		} catch (NumberFormatException e) {
			bitrates[0] = 0;
		}

		return bitrates;
	}

	/**
	 * Note: This is not exact. The bitrate can go above this but it is generally pretty good.
	 * @return The maximum bitrate the video should be along with the buffer size using MEncoder vars
	 */
	private String addMaximumBitrateConstraints(String encodeSettings, DLNAMediaInfo media, String quality, RendererConfiguration mediaRenderer, String audioType) {
		int defaultMaxBitrates[] = getVideoBitrateConfig(configuration.getMaximumBitrate());
		int rendererMaxBitrates[] = new int[2];

		if (mediaRenderer.getMaxVideoBitrate() != null) {
			rendererMaxBitrates = getVideoBitrateConfig(mediaRenderer.getMaxVideoBitrate());
		}

		if ((rendererMaxBitrates[0] > 0) && ((defaultMaxBitrates[0] == 0) || (rendererMaxBitrates[0] < defaultMaxBitrates[0]))) {
			defaultMaxBitrates = rendererMaxBitrates;
		}

		if (mediaRenderer.getCBRVideoBitrate() == 0 && defaultMaxBitrates[0] > 0 && !quality.contains("vrc_buf_size") && !quality.contains("vrc_maxrate") && !quality.contains("vbitrate")) {
			// Convert value from Mb to Kb
			defaultMaxBitrates[0] = 1000 * defaultMaxBitrates[0];

			// Half it since it seems to send up to 1 second of video in advance
			defaultMaxBitrates[0] = defaultMaxBitrates[0] / 2;

			int bufSize = 1835;
			if (media.isHDVideo()) {
				bufSize = defaultMaxBitrates[0] / 3;
			}

			if (bufSize > 7000) {
				bufSize = 7000;
			}

			if (defaultMaxBitrates[1] > 0) {
				bufSize = defaultMaxBitrates[1];
			}

			if (mediaRenderer.isDefaultVBVSize() && rendererMaxBitrates[1] == 0) {
				bufSize = 1835;
			}

			// Make room for audio
			// If audio is PCM, subtract 4600kb/s
			if ("pcm".equals(audioType)) {
				defaultMaxBitrates[0] = defaultMaxBitrates[0] - 4600;
			}
			// If audio is DTS, subtract 1510kb/s
			else if ("dts".equals(audioType)) {
				defaultMaxBitrates[0] = defaultMaxBitrates[0] - 1510;
			}
			// If audio is AC3, subtract 640kb/s to be safe
			else if ("ac3".equals(audioType)) {
				defaultMaxBitrates[0] = defaultMaxBitrates[0] - 640;
			}

			// Round down to the nearest Mb
			defaultMaxBitrates[0] = defaultMaxBitrates[0] / 1000 * 1000;

			encodeSettings += ":vrc_maxrate=" + defaultMaxBitrates[0] + ":vrc_buf_size=" + bufSize;
		}

		return encodeSettings;
	}

	/*
	 * collapse the multiple internal ways of saying "subtitles are disabled" into a single method
	 * which returns true if any of the following are true:
	 *
	 *     1) configuration.isMencoderDisableSubs()
	 *     2) params.sid == null
	 *     3) avisynth()
	 */
	private boolean isDisableSubtitles(OutputParams params) {
		return configuration.isDisableSubtitles() || (params.sid == null) || avisynth();
	}

	@Override
	public ProcessWrapper launchTranscode(
		DLNAResource dlna,
		DLNAMediaInfo media,
		OutputParams params
	) throws IOException {
		params.manageFastStart();

		boolean avisynth = avisynth();

		final String filename = dlna.getSystemName();
		setAudioAndSubs(filename, media, params, configuration);
		String externalSubtitlesFileName = null;

		if (params.sid != null && params.sid.isExternal()) {
			if (params.sid.isExternalFileUtf16()) {
				// convert UTF-16 -> UTF-8
				File convertedSubtitles = new File(PMS.getConfiguration().getTempFolder(), "utf8_" + params.sid.getExternalFile().getName());
				FileUtil.convertFileFromUtf16ToUtf8(params.sid.getExternalFile(), convertedSubtitles);
				externalSubtitlesFileName = ProcessUtil.getShortFileNameIfWideChars(convertedSubtitles.getAbsolutePath());
			} else {
				externalSubtitlesFileName = ProcessUtil.getShortFileNameIfWideChars(params.sid.getExternalFile().getAbsolutePath());
			}
		}

		InputFile newInput = new InputFile();
		newInput.setFilename(filename);
		newInput.setPush(params.stdin);

		dvd = false;

		if (media != null && media.getDvdtrack() > 0) {
			dvd = true;
		}

		ovccopy = false;
		pcm = false;
		ac3Remux = false;
		dtsRemux = false;
		wmv = false;

		int intOCW = 0;
		int intOCH = 0;

		try {
			intOCW = Integer.parseInt(configuration.getMencoderOverscanCompensationWidth());
		} catch (NumberFormatException e) {
			logger.error("Cannot parse configured MEncoder overscan compensation width: \"{}\"", configuration.getMencoderOverscanCompensationWidth());
		}

		try {
			intOCH = Integer.parseInt(configuration.getMencoderOverscanCompensationHeight());
		} catch (NumberFormatException e) {
			logger.error("Cannot parse configured MEncoder overscan compensation height: \"{}\"", configuration.getMencoderOverscanCompensationHeight());
		}

		if (params.sid == null && dvd && configuration.isMencoderRemuxMPEG2() && params.mediaRenderer.isMpeg2Supported()) {
			String expertOptions[] = getSpecificCodecOptions(
				configuration.getMencoderCodecSpecificConfig(),
				media,
				params,
				filename,
				externalSubtitlesFileName,
				configuration.isMencoderIntelligentSync(),
				false
			);

			boolean nomux = false;

			for (String s : expertOptions) {
				if (s.equals("-nomux")) {
					nomux = true;
				}
			}

			if (!nomux) {
				ovccopy = true;
			}
		}

		String vcodec = "mpeg2video";

		if (params.mediaRenderer.isTranscodeToWMV()) {
			wmv = true;
			vcodec = "wmv2"; // http://wiki.megaframe.org/wiki/Ubuntu_XBOX_360#MEncoder not usable in streaming
		}

		mpegts = params.mediaRenderer.isTranscodeToMPEGTSAC3();

		/*
		 Disable AC-3 remux for stereo tracks with 384 kbits bitrate and PS3 renderer (PS3 FW bug?)
		 TODO check new firmwares
		 Commented out until we can find a way to detect when a video has an audio track that switches from 2 to 6 channels
		 because MEncoder can't handle those files, which are very common these days.
		*/
		// final boolean ps3_and_stereo_and_384_kbits = params.aid != null
		//	&& (params.mediaRenderer.isPS3() && params.aid.getAudioProperties().getNumberOfChannels() == 2)
		//	&& (params.aid.getBitRate() > 370000 && params.aid.getBitRate() < 400000);
		final boolean ps3_and_stereo_and_384_kbits = false;

		final boolean isTSMuxerVideoEngineEnabled = PMS.getConfiguration().getEnginesAsList().contains(TsMuxeRVideo.ID);
		final boolean mencoderAC3RemuxAudioDelayBug = (params.aid != null) && (params.aid.getAudioProperties().getAudioDelay() != 0) && (params.timeseek == 0);
		if (!mencoderAC3RemuxAudioDelayBug && configuration.isAudioRemuxAC3() && params.aid != null && params.aid.isAC3() && !ps3_and_stereo_and_384_kbits && !avisynth() && params.mediaRenderer.isTranscodeToAC3()) {
			// AC3 remux takes priority
			ac3Remux = true;
		} else {
			// now check for DTS remux and LPCM streaming
			dtsRemux = isTSMuxerVideoEngineEnabled && configuration.isAudioEmbedDtsInPcm() &&
				(
					!dvd ||
					configuration.isMencoderRemuxMPEG2()
				) && params.aid != null &&
				params.aid.isDTS() &&
				!avisynth() &&
				params.mediaRenderer.isDTSPlayable();
			pcm = isTSMuxerVideoEngineEnabled && configuration.isAudioUsePCM() &&
				(
					!dvd ||
					configuration.isMencoderRemuxMPEG2()
				)
				// disable LPCM transcoding for MP4 container with non-H264 video as workaround for mencoder's A/V sync bug
				&& !(media.getContainer().equals("mp4") && !media.getCodecV().equals("h264"))
				&& params.aid != null &&
				(
					(params.aid.isDTS() && params.aid.getAudioProperties().getNumberOfChannels() <= 6) || // disable 7.1 DTS-HD => LPCM because of channels mapping bug
					params.aid.isLossless() ||
					params.aid.isTrueHD() ||
					(
						!configuration.isMencoderUsePcmForHQAudioOnly() &&
						(
							params.aid.isAC3() ||
							params.aid.isMP3() ||
							params.aid.isAAC() ||
							params.aid.isVorbis() ||
							// disable WMA to LPCM transcoding because of mencoder's channel mapping bug
							// (see CodecUtil.getMixerOutput)
							// params.aid.isWMA() ||
							params.aid.isMpegAudio()
						)
					)
				) && params.mediaRenderer.isLPCMPlayable();
		}

		if (dtsRemux || pcm) {
			params.losslessaudio = true;
			params.forceFps = media.getValidFps(false);
		}

		// mpeg2 remux still buggy with mencoder :\
		// TODO when we can still use it?
		ovccopy = false;

		if (pcm && avisynth()) {
			params.avidemux = true;
		}

		int channels;
		if (ac3Remux) {
			channels = params.aid.getAudioProperties().getNumberOfChannels(); // ac3 remux
		} else if (dtsRemux || wmv) {
			channels = 2;
		} else if (pcm) {
			channels = params.aid.getAudioProperties().getNumberOfChannels();
		} else {
			channels = configuration.getAudioChannelCount(); // 5.1 max for ac3 encoding
		}

		logger.trace("channels=" + channels);

		String add = "";
		String rendererMencoderOptions = params.mediaRenderer.getCustomMencoderOptions(); // default: empty string
		String globalMencoderOptions = configuration.getMencoderCustomOptions(); // default: empty string

		if (params.mediaRenderer.isPadVideoWithBlackBordersTo169AR()) {
			rendererMencoderOptions += " -vf softskip,expand=::::1:16/9:4";
		}

		String combinedCustomOptions = defaultString(globalMencoderOptions)
			+ " "
			+ defaultString(rendererMencoderOptions);

		if (!combinedCustomOptions.contains("-lavdopts")) {
			add = " -lavdopts debug=0";
		}

		if (isNotBlank(rendererMencoderOptions)) {
			// don't use the renderer-specific options if they break DVD streaming
			// XXX we should weed out the unused/unwanted settings and keep the rest
			// (see sanitizeArgs()) rather than ignoring the options entirely
			if (dvd && rendererMencoderOptions.contains("expand=")) {
				logger.warn("renderer MEncoder options are incompatible with DVD streaming; ignoring: " + rendererMencoderOptions);
				rendererMencoderOptions = null;
			}
		}

		StringTokenizer st = new StringTokenizer(
			"-channels " + channels
			+ (isNotBlank(globalMencoderOptions) ? " " + globalMencoderOptions : "")
			+ (isNotBlank(rendererMencoderOptions) ? " " + rendererMencoderOptions : "")
			+ add,
			" "
		);

		// XXX why does this field (which is used to populate the array returned by args(),
		// called below) store the renderer-specific (i.e. not global) MEncoder options?
		overriddenMainArgs = new String[st.countTokens()];

		{
			int nThreads = (dvd || filename.toLowerCase().endsWith("dvr-ms")) ?
				1 :
				configuration.getMencoderMaxThreads();
			boolean handleToken = false;
			int i = 0;

			while (st.hasMoreTokens()) {
				String token = st.nextToken().trim();

				if (handleToken) {
					token += ":threads=" + nThreads;

					if (configuration.getSkipLoopFilterEnabled() && !avisynth()) {
						token += ":skiploopfilter=all";
					}

					handleToken = false;
				}

				if (token.toLowerCase().contains("lavdopts")) {
					handleToken = true;
				}

				overriddenMainArgs[i++] = token;
			}
		}

		if (configuration.getMPEG2MainSettings() != null) {
			String mpeg2Options = configuration.getMPEG2MainSettings();
			String mpeg2OptionsRenderer = params.mediaRenderer.getCustomMEncoderMPEG2Options();

			// Renderer settings take priority over user settings
			if (isNotBlank(mpeg2OptionsRenderer)) {
				mpeg2Options = mpeg2OptionsRenderer;
			} else {
				// Remove comment from the value
				if (mpeg2Options.contains("/*")) {
					mpeg2Options = mpeg2Options.substring(mpeg2Options.indexOf("/*"));
				}

				// Find out the maximum bandwidth we are supposed to use
				int defaultMaxBitrates[] = getVideoBitrateConfig(configuration.getMaximumBitrate());
				int rendererMaxBitrates[] = new int[2];

				if (params.mediaRenderer.getMaxVideoBitrate() != null) {
					rendererMaxBitrates = getVideoBitrateConfig(params.mediaRenderer.getMaxVideoBitrate());
				}

				if ((rendererMaxBitrates[0] > 0) && (rendererMaxBitrates[0] < defaultMaxBitrates[0])) {
					defaultMaxBitrates = rendererMaxBitrates;
				}

				int maximumBitrate = defaultMaxBitrates[0];

				// Determine a good quality setting based on video attributes
				if (mpeg2Options.contains("Automatic")) {
					mpeg2Options = "keyint=5:vqscale=1:vqmin=2:vqmax=3";

					// It has been reported that non-PS3 renderers prefer keyint 5 but prefer it for PS3 because it lowers the average bitrate
					if (params.mediaRenderer.isPS3()) {
						mpeg2Options = "keyint=25:vqscale=1:vqmin=2:vqmax=3";
					}

					if (mpeg2Options.contains("Wireless") || maximumBitrate < 70) {
						// Lower quality for 720p+ content
						if (media.getWidth() > 1280) {
							mpeg2Options = "keyint=25:vqmax=7:vqmin=2";
						} else if (media.getWidth() > 720) {
							mpeg2Options = "keyint=25:vqmax=5:vqmin=2";
						}
					}
				}
			}

			// Ditlew - WDTV Live (+ other byte asking clients), CBR. This probably ought to be placed in addMaximumBitrateConstraints(..)
			int cbr_bitrate = params.mediaRenderer.getCBRVideoBitrate();
			String cbr_settings = (cbr_bitrate > 0) ?
					":vrc_buf_size=5000:vrc_minrate=" + cbr_bitrate + ":vrc_maxrate=" + cbr_bitrate + ":vbitrate=" + ((cbr_bitrate > 16000) ? cbr_bitrate * 1000 : cbr_bitrate) :
					"";

			String encodeSettings = "-lavcopts autoaspect=1:vcodec=" + vcodec +
					(wmv && !params.mediaRenderer.isXBOX() ? ":acodec=wmav2:abitrate=448" : (cbr_settings + ":acodec=" + (configuration.isMencoderAc3Fixed() ? "ac3_fixed" : "ac3") +
							":abitrate=" + CodecUtil.getAC3Bitrate(configuration, params.aid))) +
					":threads=" + (wmv && !params.mediaRenderer.isXBOX() ? 1 : configuration.getMencoderMaxThreads()) +
					("".equals(mpeg2Options) ? "" : ":" + mpeg2Options);

			String audioType = "ac3";
			if (dtsRemux) {
				audioType = "dts";
			} else if (pcm) {
				audioType = "pcm";
			}

			encodeSettings = addMaximumBitrateConstraints(encodeSettings, media, mpeg2Options, params.mediaRenderer, audioType);
			st = new StringTokenizer(encodeSettings, " ");

			{
				int i = overriddenMainArgs.length; // Old length
				overriddenMainArgs = Arrays.copyOf(overriddenMainArgs, overriddenMainArgs.length + st.countTokens());

				while (st.hasMoreTokens()) {
					overriddenMainArgs[i++] = st.nextToken();
				}
			}
		}

		boolean foundNoassParam = false;

		if (media != null) {
			String expertOptions [] = getSpecificCodecOptions(
				configuration.getMencoderCodecSpecificConfig(),
				media,
				params,
				filename,
				externalSubtitlesFileName,
				configuration.isMencoderIntelligentSync(),
				false
			);

			for (String s : expertOptions) {
				if (s.equals("-noass")) {
					foundNoassParam = true;
				}
			}
		}

		ArrayList<String> subtitleArgs = new ArrayList<String>();

		// Set subtitles options
		if (!configuration.isDisableSubtitles() && !avisynth() && params.sid != null) {
			int subtitleMargin = 0;
			int userMargin     = 0;

			// Use ASS flag (and therefore ASS font styles) for all subtitled files except vobsub, PGS and dvd
			boolean apply_ass_styling = params.sid.getType() != SubtitleType.VOBSUB &&
					params.sid.getType() != SubtitleType.PGS &&
					configuration.isMencoderAss() &&   // GUI: enable subtitles formating
					!foundNoassParam &&                // GUI: codec specific options
					!dvd;

			if (apply_ass_styling) {
				subtitleArgs.add("-ass");

				// GUI: Override ASS subtitles style if requested (always for SRT and TX3G subtitles)
				boolean override_ass_style = !configuration.isMencoderAssDefaultStyle() ||
						params.sid.getType() == SubtitleType.SUBRIP ||
						params.sid.getType() == SubtitleType.TX3G;

				if (override_ass_style) {
					String assSubColor = "ffffff00";

					if (configuration.getSubsColor() != 0) {
						assSubColor = Integer.toHexString(configuration.getSubsColor());
						if (assSubColor.length() > 2) {
							assSubColor = assSubColor.substring(2) + "00";
						}
					}

					subtitleArgs.add("-ass-color");
					subtitleArgs.add(assSubColor);
					subtitleArgs.add("-ass-border-color");
					subtitleArgs.add("00000000");
					subtitleArgs.add("-ass-font-scale");
					subtitleArgs.add(configuration.getAssScale());
					StringBuilder assForceStyle = new StringBuilder();

					// set subtitles font
					if (configuration.getFont() != null && configuration.getFont().length() > 0) {
						// set font with -font option, workaround for
						// https://github.com/Happy-Neko/ps3mediaserver/commit/52e62203ea12c40628de1869882994ce1065446a#commitcomment-990156 bug
						subtitleArgs.add("-font");
						subtitleArgs.add(configuration.getFont());
						assForceStyle.append("FontName=").append(quoteArg(configuration.getFont())).append(",");
					} else {
						String font = CodecUtil.getDefaultFontPath();
						if (isNotBlank(font)) {
							// Variable "font" contains a font path instead of a font name.
							// Does "-ass-force-style" support font paths? In tests on OS X
							// the font path is ignored (Outline, Shadow and MarginV are
							// used, though) and the "-font" definition is used instead.
							// See: https://github.com/ps3mediaserver/ps3mediaserver/pull/14
							subtitleArgs.add("-font");
							subtitleArgs.add(font);
							assForceStyle.append("FontName=").append(quoteArg(font)).append(",");
						} else {
							subtitleArgs.add("-font");
							subtitleArgs.add("Arial");
							assForceStyle.append("FontName=Arial,");
						}
					}

					// Add to the subtitle margin if overscan compensation is being used
					// This keeps the subtitle text inside the frame instead of in the border
					if (intOCH > 0) {
						subtitleMargin = (media.getHeight() / 100) * intOCH;
					}

					assForceStyle.append("Outline=").append(configuration.getAssOutline());
					assForceStyle.append(",Shadow=").append(configuration.getAssShadow());

					try {
						userMargin = Integer.parseInt(configuration.getAssMargin());
					} catch (NumberFormatException n) {
						logger.debug("Could not parse SSA margin from \"" + configuration.getAssMargin() + "\"");
					}

					subtitleMargin = subtitleMargin + userMargin;
					assForceStyle.append(",MarginV=").append(subtitleMargin);
					subtitleArgs.add("-ass-force-style");
					subtitleArgs.add(assForceStyle.toString());
				} else if (intOCH > 0) {
					subtitleArgs.add("-ass-force-style");
					subtitleArgs.add("MarginV=" + subtitleMargin);
				}

				// MEncoder is not compiled with fontconfig on Mac OS X, therefore
				// use of the "-ass" option also requires the "-font" option.
				if (Platform.isMac() && !subtitleArgs.contains("-font")) {
					String font = CodecUtil.getDefaultFontPath();

					if (isNotBlank(font)) {
						subtitleArgs.add("-font");
						subtitleArgs.add(font);
					}
				}

				// Workaround for MPlayer #2041, remove when that bug is fixed
				if (!params.sid.isEmbedded()) {
					subtitleArgs.add("-noflip-hebrew");
				}
			// use PLAINTEXT formating
			} else {
				// set subtitles font
				if (configuration.getFont() != null && configuration.getFont().length() > 0) {
					subtitleArgs.add("-font");
					subtitleArgs.add(configuration.getFont());
				} else {
					String font = CodecUtil.getDefaultFontPath();

					if (isNotBlank(font)) {
						subtitleArgs.add("-font");
						subtitleArgs.add(font);
					}
				}

				subtitleArgs.add("-subfont-text-scale");
				subtitleArgs.add(configuration.getMencoderNoAssScale());
				subtitleArgs.add("-subfont-outline");
				subtitleArgs.add(configuration.getMencoderNoAssOutline());
				subtitleArgs.add("-subfont-blur");
				subtitleArgs.add(configuration.getMencoderNoAssBlur());

				// Add to the subtitle margin if overscan compensation is being used
				// This keeps the subtitle text inside the frame instead of in the border
				if (intOCH > 0) {
					subtitleMargin = intOCH;
				}

				try {
					userMargin = Integer.parseInt(configuration.getMencoderNoAssSubPos());
				} catch (NumberFormatException n) {
					logger.debug("Could not parse subpos from \"" + configuration.getMencoderNoAssSubPos() + "\"");
				}

				subtitleMargin = subtitleMargin + userMargin;

				subtitleArgs.add("-subpos");
				subtitleArgs.add(String.valueOf(100 - subtitleMargin));
			}

			// Common subtitle options

			// MEncoder on Mac OS X is compiled without fontconfig support.
			// Appending the flag will break execution, so skip it on Mac OS X.
			if (!Platform.isMac()) {
				// Use fontconfig if enabled
				if (configuration.isMencoderFontConfig()) {
					subtitleArgs.add("-fontconfig");
				} else {
					subtitleArgs.add("-nofontconfig");
				}
			}

			// Apply DVD/VOBSUB subtitle quality
			if (params.sid.getType() == SubtitleType.VOBSUB && configuration.getMencoderVobsubSubtitleQuality() != null) {
				String subtitleQuality = configuration.getMencoderVobsubSubtitleQuality();

				subtitleArgs.add("-spuaa");
				subtitleArgs.add(subtitleQuality);
			}

			// external subtitles file
			if (params.sid.isExternal()) {
				if (!params.sid.isExternalFileUtf()) {
					String subcp = null;

					// append -subcp option for non UTF external subtitles
					if (isNotBlank(configuration.getSubtitlesCodepage())) {
						// manual setting
						subcp = configuration.getSubtitlesCodepage();
					} else if (isNotBlank(SubtitleUtils.getSubCpOptionForMencoder(params.sid))) {
						// autodetect charset (blank mencoder_subcp config option)
						subcp = SubtitleUtils.getSubCpOptionForMencoder(params.sid);
					}

					if (isNotBlank(subcp)) {
						subtitleArgs.add("-subcp");
						subtitleArgs.add(subcp);

						if (configuration.isMencoderSubFribidi()) {
							subtitleArgs.add("-fribidi-charset");
							subtitleArgs.add(subcp);
						}
					}
				}
			}
		}

		int index = overriddenMainArgs.length;
		overriddenMainArgs = Arrays.copyOf(overriddenMainArgs, overriddenMainArgs.length + subtitleArgs.size());

		for (String subtitleArg : subtitleArgs) {
			overriddenMainArgs[index] = subtitleArg;
			index++;
		}

		List<String> cmdList = new ArrayList<String>();

		cmdList.add(executable());

		// timeseek
		// XXX -ss 0 is is included for parity with the old (cmdArray) code: it may be possible to omit it
		cmdList.add("-ss");
		cmdList.add((params.timeseek > 0) ? "" + params.timeseek : "0");

		if (dvd) {
			cmdList.add("-dvd-device");
		}

		// input filename
		if (avisynth && !filename.toLowerCase().endsWith(".iso")) {
			File avsFile = FFmpegAviSynthVideo.getAVSScript(filename, params.sid, params.fromFrame, params.toFrame);
			cmdList.add(ProcessUtil.getShortFileNameIfWideChars(avsFile.getAbsolutePath()));
		} else {
			if (params.stdin != null) {
				cmdList.add("-");
			} else {
				cmdList.add(filename);
			}
		}

		if (dvd) {
			cmdList.add("dvd://" + media.getDvdtrack());
		}

		for (String arg : args()) {
			if (arg.contains("format=mpeg2") && media.getAspect() != null && media.getValidAspect(true) != null) {
				cmdList.add(arg + ":vaspect=" + media.getValidAspect(true));
			} else {
				cmdList.add(arg);
			}
		}

		if (!dtsRemux && !pcm && !avisynth() && params.aid != null && media.getAudioTracksList().size() > 1) {
			cmdList.add("-aid");
			boolean lavf = false; // TODO Need to add support for LAVF demuxing
			cmdList.add("" + (lavf ? params.aid.getId() + 1 : params.aid.getId()));
		}

		/*
		 * handle subtitles
		 *
		 * try to reconcile the fact that the handling of "Disable subtitles" is spread out
		 * over net.pms.encoders.Player.setAudioAndSubs and here by setting both of MEncoder's "disable
		 * subs" options if any of the internal conditions for disabling subtitles are met.
		 */
		if (isDisableSubtitles(params)) {
			// Ensure that internal subtitles are not automatically loaded
			// MKV: in some circumstances, MEncoder automatically selects an internal sub unless we explicitly disable (internal) subtitles
			// http://www.ps3mediaserver.org/forum/viewtopic.php?f=14&t=15891
			cmdList.add("-nosub");
			// Ensure that external subtitles are not automatically loaded
			cmdList.add("-noautosub");
		} else {
			// note: isEmbedded() and isExternal() are mutually exclusive
			if (params.sid.isEmbedded()) { // internal (embedded) subs
				// Ensure that external subtitles are not automatically loaded
				cmdList.add("-noautosub");
				// Specify which internal subtitle we want
				cmdList.add("-sid");
				cmdList.add("" + params.sid.getId());
			} else if (externalSubtitlesFileName != null) { // external subtitles
				assert params.sid.isExternal(); // confirm the mutual exclusion

				// Ensure that internal subtitles are not automatically loaded
				cmdList.add("-nosub");

				if (params.sid.getType() == SubtitleType.VOBSUB) {
					cmdList.add("-vobsub");
					cmdList.add(externalSubtitlesFileName.substring(0, externalSubtitlesFileName.length() - 4));
					cmdList.add("-slang");
					cmdList.add("" + params.sid.getLang());
				} else {
					cmdList.add("-sub");
					cmdList.add(externalSubtitlesFileName.replace(",", "\\,")); // Commas in MEncoder separate multiple subtitle files

					if (params.sid.isExternalFileUtf()) {
						// append -utf8 option for UTF-8 external subtitles
						cmdList.add("-utf8");
					}
				}
			}
		}

		// -ofps
		String validFramerate = (media != null) ? media.getValidFps(true) : null; // optional input framerate: may be null
		String framerate = (validFramerate != null) ? validFramerate : "24000/1001"; // where a framerate is required, use the input framerate or 24000/1001
		String ofps = framerate;

		// optional -fps or -mc
		if (configuration.isMencoderForceFps()) {
			if (!configuration.isFix25FPSAvMismatch()) {
				cmdList.add("-fps");
				cmdList.add(framerate);
			} else if (validFramerate != null) { // XXX not sure why this "fix" requires the input to have a valid framerate, but that's the logic in the old (cmdArray) code
				cmdList.add("-mc");
				cmdList.add("0.005");
				ofps = "25";
			}
		}

		cmdList.add("-ofps");
		cmdList.add(ofps);

		/*
		 * TODO: Move the following block up with the rest of the
		 * subtitle stuff
		 */
		// external subtitles file
		if (!configuration.isDisableSubtitles() && !avisynth() && params.sid != null && params.sid.isExternal()) {
			if (params.sid.getType() == SubtitleType.VOBSUB) {
				cmdList.add("-vobsub");
				cmdList.add(externalSubtitlesFileName.substring(0, externalSubtitlesFileName.length() - 4));
				cmdList.add("-slang");
				cmdList.add("" + params.sid.getLang());
			} else {
				cmdList.add("-sub");
				cmdList.add(externalSubtitlesFileName.replace(",", "\\,")); // Commas in MEncoder separate multiple subtitle files

				if (params.sid.isExternalFileUtf()) {
					// append -utf8 option for UTF-8 external subtitles
					cmdList.add("-utf8");
				}
			}
		}

		if (filename.toLowerCase().endsWith(".evo")) {
			cmdList.add("-psprobe");
			cmdList.add("10000");
		}

		boolean deinterlace = configuration.isMencoderYadif();

		// Check if the media renderer supports this resolution
		boolean isResolutionTooHighForRenderer = params.mediaRenderer.isVideoRescale()
			&& media != null
			&& (
				(media.getWidth() > params.mediaRenderer.getMaxVideoWidth())
				||
				(media.getHeight() > params.mediaRenderer.getMaxVideoHeight())
			);

		// Video scaler and overscan compensation
		boolean scaleBool = isResolutionTooHighForRenderer
			|| (configuration.isMencoderScaler() && (configuration.getMencoderScaleX() != 0 || configuration.getMencoderScaleY() != 0))
			|| (intOCW > 0 || intOCH > 0);

		if ((deinterlace || scaleBool) && !avisynth()) {
			StringBuilder vfValueOverscanPrepend = new StringBuilder();
			StringBuilder vfValueOverscanMiddle  = new StringBuilder();
			StringBuilder vfValueVS              = new StringBuilder();
			StringBuilder vfValueComplete        = new StringBuilder();

			String deinterlaceComma = "";
			int scaleWidth = 0;
			int scaleHeight = 0;
			double rendererAspectRatio;

			// Set defaults
			if (media != null && media.getWidth() > 0 && media.getHeight() > 0) {
				scaleWidth = media.getWidth();
				scaleHeight = media.getHeight();
			}

			/*
			 * Implement overscan compensation settings
			 *
			 * This feature takes into account aspect ratio,
			 * making it less blunt than the Video Scaler option
			 */
			if (intOCW > 0 || intOCH > 0) {
				int intOCWPixels = (media.getWidth()  / 100) * intOCW;
				int intOCHPixels = (media.getHeight() / 100) * intOCH;

				scaleWidth  = scaleWidth  + intOCWPixels;
				scaleHeight = scaleHeight + intOCHPixels;

				// See if the video needs to be scaled down
				if (
					params.mediaRenderer.isVideoRescale() &&
					(
						(scaleWidth > params.mediaRenderer.getMaxVideoWidth()) ||
						(scaleHeight > params.mediaRenderer.getMaxVideoHeight())
					)
				) {
					double overscannedAspectRatio = scaleWidth / scaleHeight;
					rendererAspectRatio = params.mediaRenderer.getMaxVideoWidth() / params.mediaRenderer.getMaxVideoHeight();

					if (overscannedAspectRatio > rendererAspectRatio) {
						// Limit video by width
						scaleWidth  = params.mediaRenderer.getMaxVideoWidth();
						scaleHeight = (int) Math.round(params.mediaRenderer.getMaxVideoWidth() / overscannedAspectRatio);
					} else {
						// Limit video by height
						scaleWidth  = (int) Math.round(params.mediaRenderer.getMaxVideoHeight() * overscannedAspectRatio);
						scaleHeight = params.mediaRenderer.getMaxVideoHeight();
					}
				}

				vfValueOverscanPrepend.append("softskip,expand=-").append(intOCWPixels).append(":-").append(intOCHPixels);
				vfValueOverscanMiddle.append(",scale=").append(scaleWidth).append(":").append(scaleHeight);
			}

			/*
			 * Video Scaler and renderer-specific resolution-limiter
			 */
			if (configuration.isMencoderScaler()) {
				// Use the manual, user-controlled scaler
				if (configuration.getMencoderScaleX() != 0) {
					if (configuration.getMencoderScaleX() <= params.mediaRenderer.getMaxVideoWidth()) {
						scaleWidth = configuration.getMencoderScaleX();
					} else {
						scaleWidth = params.mediaRenderer.getMaxVideoWidth();
					}
				}

				if (configuration.getMencoderScaleY() != 0) {
					if (configuration.getMencoderScaleY() <= params.mediaRenderer.getMaxVideoHeight()) {
						scaleHeight = configuration.getMencoderScaleY();
					} else {
						scaleHeight = params.mediaRenderer.getMaxVideoHeight();
					}
				}

				logger.info("Setting video resolution to: " + scaleWidth + "x" + scaleHeight + ", your Video Scaler setting");

				vfValueVS.append("scale=").append(scaleWidth).append(":").append(scaleHeight);

			/*
			 * The video resolution is too big for the renderer so we need to scale it down
			 */
			} else if (
				media != null &&
				media.getWidth() > 0 &&
				media.getHeight() > 0 &&
				(
					media.getWidth()  > params.mediaRenderer.getMaxVideoWidth() ||
					media.getHeight() > params.mediaRenderer.getMaxVideoHeight()
				)
			) {
				double videoAspectRatio = (double) media.getWidth() / (double) media.getHeight();
				rendererAspectRatio = (double) params.mediaRenderer.getMaxVideoWidth() / (double) params.mediaRenderer.getMaxVideoHeight();

				/*
				 * First we deal with some exceptions, then if they are not matched we will
				 * let the renderer limits work.
				 *
				 * This is so, for example, we can still define a maximum resolution of
				 * 1920x1080 in the renderer config file but still support 1920x1088 when
				 * it's needed, otherwise we would either resize 1088 to 1080, meaning the
				 * ugly (unused) bottom 8 pixels would be displayed, or we would limit all
				 * videos to 1088 causing the bottom 8 meaningful pixels to be cut off.
				 */
				if (media.getWidth() == 3840 && media.getHeight() == 1080) {
					// Full-SBS
					scaleWidth  = 1920;
					scaleHeight = 1080;
				} else if (media.getWidth() == 1920 && media.getHeight() == 2160) {
					// Full-OU
					scaleWidth  = 1920;
					scaleHeight = 1080;
				} else if (media.getWidth() == 1920 && media.getHeight() == 1088) {
					// SAT capture
					scaleWidth  = 1920;
					scaleHeight = 1088;
				} else {
					// Passed the exceptions, now we allow the renderer to define the limits
					if (videoAspectRatio > rendererAspectRatio) {
						scaleWidth  = params.mediaRenderer.getMaxVideoWidth();
						scaleHeight = (int) Math.round(params.mediaRenderer.getMaxVideoWidth() / videoAspectRatio);
					} else {
						scaleWidth  = (int) Math.round(params.mediaRenderer.getMaxVideoHeight() * videoAspectRatio);
						scaleHeight = params.mediaRenderer.getMaxVideoHeight();
					}
				}

				logger.info("Setting video resolution to: " + scaleWidth + "x" + scaleHeight + ", the maximum your renderer supports");

				vfValueVS.append("scale=").append(scaleWidth).append(":").append(scaleHeight);
			}

			// Put the string together taking into account overscan compensation and video scaler
			if (intOCW > 0 || intOCH > 0) {
				vfValueComplete.append(vfValueOverscanPrepend).append(vfValueOverscanMiddle).append(",harddup");
				logger.info("Setting video resolution to: " + scaleWidth + "x" + scaleHeight + ", to fit your overscan compensation");
			} else {
				vfValueComplete.append(vfValueVS);
			}

			if (deinterlace) {
				deinterlaceComma = ",";
			}

			String vfValue = (deinterlace ? "yadif" : "") + (scaleBool ? deinterlaceComma + vfValueComplete : "");

			if (isNotBlank(vfValue)) {
				cmdList.add("-vf");
				cmdList.add(vfValue);
			}
		}

		/*
		 * The PS3 and possibly other renderers display videos incorrectly
		 * if the dimensions aren't divisible by 4, so if that is the
		 * case we scale it down to the nearest 4.
		 * This fixes the long-time bug of videos displaying in black and
		 * white with diagonal strips of colour, weird one.
		 *
		 * TODO: Integrate this with the other stuff so that "scale" only
		 * ever appears once in the MEncoder CMD.
		 */
		if (media != null && (media.getWidth() % 4 != 0) || media.getHeight() % 4 != 0) {
			int newWidth;
			int newHeight;

			newWidth  = (media.getWidth() / 4) * 4;
			newHeight = (media.getHeight() / 4) * 4;

			cmdList.add("-vf");
			cmdList.add("softskip,expand=" + newWidth + ":" + newHeight);
		}

		if (configuration.getMencoderMT() && !avisynth && !dvd && !(startsWith(media.getCodecV(), "mpeg2"))) {
			cmdList.add("-lavdopts");
			cmdList.add("fast");
		}

		boolean disableMc0AndNoskip = false;

		// Process the options for this file in Transcoding Settings -> Mencoder -> Expert Settings: Codec-specific parameters
		// TODO this is better handled by a plugin with scripting support and will be removed
		if (media != null) {
			String expertOptions[] = getSpecificCodecOptions(
				configuration.getMencoderCodecSpecificConfig(),
				media,
				params,
				filename,
				externalSubtitlesFileName,
				configuration.isMencoderIntelligentSync(),
				false
			);

			// the parameters (expertOptions) are processed in 3 passes
			// 1) process expertOptions
			// 2) process cmdList
			// 3) append expertOptions to cmdList

			if (expertOptions != null && expertOptions.length > 0) {
				// remove this option (key) from the cmdList in pass 2.
				// if the boolean value is true, also remove the option's corresponding value
				Map<String, Boolean> removeCmdListOption = new HashMap<String, Boolean>();

				// if this option (key) is defined in cmdList, merge this string value into the
				// option's value in pass 2. the value is a string format template into which the
				// cmdList option value is injected
				Map<String, String> mergeCmdListOption = new HashMap<String, String>();

				// merges that are performed in pass 2 are logged in this map; the key (string) is
				// the option name and the value is a boolean indicating whether the option was merged
				// or not. the map is populated after pass 1 with the options from mergeCmdListOption
				// and all values initialised to false. if an option was merged, it is not appended
				// to cmdList
				Map<String, Boolean> mergedCmdListOption = new HashMap<String, Boolean>();

				// pass 1: process expertOptions
				for (int i = 0; i < expertOptions.length; ++i) {
					if (expertOptions[i].equals("-noass")) {
						// remove -ass from cmdList in pass 2.
						// -ass won't have been added in this method (getSpecificCodecOptions
						// has been called multiple times above to check for -noass and -nomux)
						// but it may have been added via the renderer or global MEncoder options.
						// XXX: there are currently 10 other -ass options (-ass-color, -ass-border-color &c.).
						// technically, they should all be removed...
						removeCmdListOption.put("-ass", false); // false: option does not have a corresponding value
						// remove -noass from expertOptions in pass 3
						expertOptions[i] = REMOVE_OPTION;
					} else if (expertOptions[i].equals("-nomux")) {
						expertOptions[i] = REMOVE_OPTION;
					} else if (expertOptions[i].equals("-mt")) {
						// not an MEncoder option so remove it from exportOptions.
						// multi-threaded MEncoder is used by default, so this is obsolete (TODO: Remove it from the description)
						expertOptions[i] = REMOVE_OPTION;
					} else if (expertOptions[i].equals("-ofps")) {
						// replace the cmdList version with the expertOptions version i.e. remove the former
						removeCmdListOption.put("-ofps", true);
						// skip (i.e. leave unchanged) the exportOptions value
						++i;
					} else if (expertOptions[i].equals("-fps")) {
						removeCmdListOption.put("-fps", true);
						++i;
					} else if (expertOptions[i].equals("-ovc")) {
						removeCmdListOption.put("-ovc", true);
						++i;
					} else if (expertOptions[i].equals("-channels")) {
						removeCmdListOption.put("-channels", true);
						++i;
					} else if (expertOptions[i].equals("-oac")) {
						removeCmdListOption.put("-oac", true);
						++i;
					} else if (expertOptions[i].equals("-quality")) {
						// XXX like the old (cmdArray) code, this clobbers the old -lavcopts value
						String lavcopts = String.format(
							"autoaspect=1:vcodec=%s:acodec=%s:abitrate=%s:threads=%d:%s",
							vcodec,
							(configuration.isMencoderAc3Fixed() ? "ac3_fixed" : "ac3"),
							CodecUtil.getAC3Bitrate(configuration, params.aid),
							configuration.getMencoderMaxThreads(),
							expertOptions[i + 1]
						);

						// append bitrate-limiting options if configured
						lavcopts = addMaximumBitrateConstraints(
							lavcopts,
							media,
							lavcopts,
							params.mediaRenderer,
							""
						);

						// a string format with no placeholders, so the cmdList option value is ignored.
						// note: we protect "%" from being interpreted as a format by converting it to "%%",
						// which is then turned back into "%" when the format is processed
						mergeCmdListOption.put("-lavcopts", lavcopts.replace("%", "%%"));
						// remove -quality <value>
						expertOptions[i] = expertOptions[i + 1] = REMOVE_OPTION;
						++i;
					} else if (expertOptions[i].equals("-mpegopts")) {
						mergeCmdListOption.put("-mpegopts", "%s:" + expertOptions[i + 1].replace("%", "%%"));
						// merge if cmdList already contains -mpegopts, but don't append if it doesn't (parity with the old (cmdArray) version)
						expertOptions[i] = expertOptions[i + 1] = REMOVE_OPTION;
						++i;
					} else if (expertOptions[i].equals("-vf")) {
						mergeCmdListOption.put("-vf", "%s," + expertOptions[i + 1].replace("%", "%%"));
						++i;
					} else if (expertOptions[i].equals("-af")) {
						mergeCmdListOption.put("-af", "%s," + expertOptions[i + 1].replace("%", "%%"));
						++i;
					} else if (expertOptions[i].equals("-nosync")) {
						disableMc0AndNoskip = true;
						expertOptions[i] = REMOVE_OPTION;
					} else if (expertOptions[i].equals("-mc")) {
						disableMc0AndNoskip = true;
					}
				}

				for (String key : mergeCmdListOption.keySet()) {
					mergedCmdListOption.put(key, false);
				}

				// pass 2: process cmdList
				List<String> transformedCmdList = new ArrayList<String>();

				for (int i = 0; i < cmdList.size(); ++i) {
					String option = cmdList.get(i);

					// we remove an option by *not* adding it to transformedCmdList
					if (removeCmdListOption.containsKey(option)) {
						if (isTrue(removeCmdListOption.get(option))) { // true: remove (i.e. don't add) the corresponding value
							++i;
						}
					} else {
						transformedCmdList.add(option);

						if (mergeCmdListOption.containsKey(option)) {
							String format = mergeCmdListOption.get(option);
							String value = String.format(format, cmdList.get(i + 1));
							// record the fact that an expertOption value has been merged into this cmdList value
							mergedCmdListOption.put(option, true);
							transformedCmdList.add(value);
							++i;
						}
					}
				}

				cmdList = transformedCmdList;

				// pass 3: append expertOptions to cmdList
				for (int i = 0; i < expertOptions.length; ++i) {
					String option = expertOptions[i];

					if (option != REMOVE_OPTION) {
						if (isTrue(mergedCmdListOption.get(option))) { // true: this option and its value have already been merged into existing cmdList options
							++i; // skip the value
						} else {
							cmdList.add(option);
						}
					}
				}
			}
		}

		if ((pcm || dtsRemux || ac3Remux) || (configuration.isMencoderNoOutOfSync() && !disableMc0AndNoskip)) {
			if (configuration.isFix25FPSAvMismatch()) {
				cmdList.add("-mc");
				cmdList.add("0.005");
			} else {
				cmdList.add("-mc");
				cmdList.add("0");
				cmdList.add("-noskip");
			}
		}

		if (params.timeend > 0) {
			cmdList.add("-endpos");
			cmdList.add("" + params.timeend);
		}

		String rate = "48000";
		if (params.mediaRenderer.isXBOX()) {
			rate = "44100";
		}

		// force srate -> cause ac3's mencoder doesn't like anything other than 48khz
		if (media != null && !pcm && !dtsRemux && !ac3Remux) {
			cmdList.add("-af");
			cmdList.add("lavcresample=" + rate);
			cmdList.add("-srate");
			cmdList.add(rate);
		}

		// add a -cache option for piped media (e.g. rar/zip file entries):
		// https://code.google.com/p/ps3mediaserver/issues/detail?id=911
		if (params.stdin != null) {
			cmdList.add("-cache");
			cmdList.add("8192");
		}

		PipeProcess pipe = null;

		ProcessWrapperImpl pw = null;

		if (pcm || dtsRemux) {
			// transcode video, demux audio, remux with tsmuxer
			boolean channels_filter_present = false;

			for (String s : cmdList) {
				if (isNotBlank(s) && s.startsWith("channels")) {
					channels_filter_present = true;
					break;
				}
			}

			if (params.avidemux) {
				pipe = new PipeProcess("mencoder" + System.currentTimeMillis(), (pcm || dtsRemux || ac3Remux) ? null : params);
				params.input_pipes[0] = pipe;

				cmdList.add("-o");
				cmdList.add(pipe.getInputPipe());

				if (pcm && !channels_filter_present && params.aid != null) {
					String mixer = getLPCMChannelMappingForMencoder(params.aid);
					if (isNotBlank(mixer)) {
						cmdList.add("-af");
						cmdList.add(mixer);
					}
				}

				String[] cmdArray = new String[cmdList.size()];
				cmdList.toArray(cmdArray);
				pw = new ProcessWrapperImpl(cmdArray, params);

				PipeProcess videoPipe = new PipeProcess("videoPipe" + System.currentTimeMillis(), "out", "reconnect");
				PipeProcess audioPipe = new PipeProcess("audioPipe" + System.currentTimeMillis(), "out", "reconnect");

				ProcessWrapper videoPipeProcess = videoPipe.getPipeProcess();
				ProcessWrapper audioPipeProcess = audioPipe.getPipeProcess();

				params.output_pipes[0] = videoPipe;
				params.output_pipes[1] = audioPipe;

				pw.attachProcess(videoPipeProcess);
				pw.attachProcess(audioPipeProcess);
				videoPipeProcess.runInNewThread();
				audioPipeProcess.runInNewThread();
				try {
					Thread.sleep(50);
				} catch (InterruptedException e) { }
				videoPipe.deleteLater();
				audioPipe.deleteLater();
			} else {
				// remove the -oac switch, otherwise the "too many video packets" errors appear again
				for (ListIterator<String> it = cmdList.listIterator(); it.hasNext();) {
					String option = it.next();

					if (option.equals("-oac")) {
						it.set("-nosound");

						if (it.hasNext()) {
							it.next();
							it.remove();
						}

						break;
					}
				}

				pipe = new PipeProcess(System.currentTimeMillis() + "tsmuxerout.ts");

				TsMuxeRVideo ts = new TsMuxeRVideo(configuration);
				File f = new File(configuration.getTempFolder(), "pms-tsmuxer.meta");
				String cmd[] = new String[]{ ts.executable(), f.getAbsolutePath(), pipe.getInputPipe() };
				pw = new ProcessWrapperImpl(cmd, params);

				PipeIPCProcess ffVideoPipe = new PipeIPCProcess(System.currentTimeMillis() + "ffmpegvideo", System.currentTimeMillis() + "videoout", false, true);

				cmdList.add("-o");
				cmdList.add(ffVideoPipe.getInputPipe());

				OutputParams ffparams = new OutputParams(configuration);
				ffparams.maxBufferSize = 1;
				ffparams.stdin = params.stdin;

				String[] cmdArray = new String[cmdList.size()];
				cmdList.toArray(cmdArray);
				ProcessWrapperImpl ffVideo = new ProcessWrapperImpl(cmdArray, ffparams);

				ProcessWrapper ff_video_pipe_process = ffVideoPipe.getPipeProcess();
				pw.attachProcess(ff_video_pipe_process);
				ff_video_pipe_process.runInNewThread();
				ffVideoPipe.deleteLater();

				pw.attachProcess(ffVideo);
				ffVideo.runInNewThread();

				String aid = null;
				if (media != null && media.getAudioTracksList().size() > 1 && params.aid != null) {
					if (media.getContainer() != null && (media.getContainer().equals(FormatConfiguration.AVI) || media.getContainer().equals(FormatConfiguration.FLV))) {
						// TODO confirm (MP4s, OGMs and MOVs already tested: first aid is 0; AVIs: first aid is 1)
						// for AVIs, FLVs and MOVs mencoder starts audio tracks numbering from 1
						aid = "" + (params.aid.getId() + 1);
					} else {
						// everything else from 0
						aid = "" + params.aid.getId();
					}
				}

				PipeIPCProcess ffAudioPipe = new PipeIPCProcess(System.currentTimeMillis() + "ffmpegaudio01", System.currentTimeMillis() + "audioout", false, true);
				StreamModifier sm = new StreamModifier();
				sm.setPcm(pcm);
				sm.setDtsEmbed(dtsRemux);
				sm.setSampleFrequency(48000);
				sm.setBitsPerSample(16);

				String mixer = null;
				if (pcm && !dtsRemux) {
					mixer = getLPCMChannelMappingForMencoder(params.aid); // LPCM always outputs 5.1/7.1 for multichannel tracks. Downmix with player if needed!
				}

				sm.setNbChannels(channels);

				// it seems the -really-quiet prevents mencoder to stop the pipe output after some time...
				// -mc 0.1 make the DTS-HD extraction works better with latest mencoder builds, and makes no impact on the regular DTS one
				String ffmpegLPCMextract[] = new String[]{
					executable(),
					"-ss", "0",
					filename,
					"-really-quiet",
					"-msglevel", "statusline=2",
					"-channels", "" + channels,
					"-ovc", "copy",
					"-of", "rawaudio",
					"-mc", dtsRemux ? "0.1" : "0",
					"-noskip",
					(aid == null) ? "-quiet" : "-aid", (aid == null) ? "-quiet" : aid,
					"-oac", (ac3Remux || dtsRemux) ? "copy" : "pcm",
					(isNotBlank(mixer) && !channels_filter_present) ? "-af" : "-quiet", (isNotBlank(mixer) && !channels_filter_present) ? mixer : "-quiet",
					"-srate", "48000",
					"-o", ffAudioPipe.getInputPipe()
				};

				if (!params.mediaRenderer.isMuxDTSToMpeg()) { // no need to use the PCM trick when media renderer supports DTS
					ffAudioPipe.setModifier(sm);
				}

				if (media != null && media.getDvdtrack() > 0) {
					ffmpegLPCMextract[3] = "-dvd-device";
					ffmpegLPCMextract[4] = filename;
					ffmpegLPCMextract[5] = "dvd://" + media.getDvdtrack();
				} else if (params.stdin != null) {
					ffmpegLPCMextract[3] = "-";
				}

				if (filename.toLowerCase().endsWith(".evo")) {
					ffmpegLPCMextract[4] = "-psprobe";
					ffmpegLPCMextract[5] = "1000000";
				}

				if (params.timeseek > 0) {
					ffmpegLPCMextract[2] = "" + params.timeseek;
				}

				OutputParams ffaudioparams = new OutputParams(configuration);
				ffaudioparams.maxBufferSize = 1;
				ffaudioparams.stdin = params.stdin;
				ProcessWrapperImpl ffAudio = new ProcessWrapperImpl(ffmpegLPCMextract, ffaudioparams);

				params.stdin = null;

				PrintWriter pwMux = new PrintWriter(f);
				pwMux.println("MUXOPT --no-pcr-on-video-pid --no-asyncio --new-audio-pes --vbr --vbv-len=500");
				String videoType = "V_MPEG-2";

				if (params.no_videoencode && params.forceType != null) {
					videoType = params.forceType;
				}

				String fps = "";
				if (params.forceFps != null) {
					fps = "fps=" + params.forceFps + ", ";
				}

				String audioType;
				if (ac3Remux) {
					audioType = "A_AC3";
				} else if (dtsRemux) {
					if (params.mediaRenderer.isMuxDTSToMpeg()) {
						//renderer can play proper DTS track
						audioType = "A_DTS";
					} else {
						// DTS padded in LPCM trick
						audioType = "A_LPCM";
					}
				} else {
					// PCM
					audioType = "A_LPCM";
				}


				// mencoder bug (confirmed with mencoder r35003 + ffmpeg 0.11.1):
				// audio delay is ignored when playing from file start (-ss 0)
				// override with tsmuxer.meta setting
				String timeshift = "";
				if (mencoderAC3RemuxAudioDelayBug) {
					timeshift = "timeshift=" + params.aid.getAudioProperties().getAudioDelay() + "ms, ";
				}

				pwMux.println(videoType + ", \"" + ffVideoPipe.getOutputPipe() + "\", " + fps + "level=4.1, insertSEI, contSPS, track=1");
				pwMux.println(audioType + ", \"" + ffAudioPipe.getOutputPipe() + "\", " + timeshift + "track=2");
				pwMux.close();

				ProcessWrapper pipe_process = pipe.getPipeProcess();
				pw.attachProcess(pipe_process);
				pipe_process.runInNewThread();

				try {
					Thread.sleep(50);
				} catch (InterruptedException e) {
				}

				pipe.deleteLater();
				params.input_pipes[0] = pipe;

				ProcessWrapper ff_pipe_process = ffAudioPipe.getPipeProcess();
				pw.attachProcess(ff_pipe_process);
				ff_pipe_process.runInNewThread();

				try {
					Thread.sleep(50);
				} catch (InterruptedException e) {
				}

				ffAudioPipe.deleteLater();
				pw.attachProcess(ffAudio);
				ffAudio.runInNewThread();
			}
		} else {
			boolean directpipe = Platform.isMac() || Platform.isFreeBSD();

			if (directpipe) {
				cmdList.add("-o");
				cmdList.add("-");
				cmdList.add("-really-quiet");
				cmdList.add("-msglevel");
				cmdList.add("statusline=2");
				params.input_pipes = new PipeProcess[2];
			} else {
				pipe = new PipeProcess("mencoder" + System.currentTimeMillis(), (pcm || dtsRemux) ? null : params);
				params.input_pipes[0] = pipe;
				cmdList.add("-o");
				cmdList.add(pipe.getInputPipe());
			}

			String[] cmdArray = new String[ cmdList.size() ];
			cmdList.toArray(cmdArray);

			cmdArray = finalizeTranscoderArgs(
				filename,
				dlna,
				media,
				params,
				cmdArray
			);

			pw = new ProcessWrapperImpl(cmdArray, params);

			if (!directpipe) {
				ProcessWrapper mkfifo_process = pipe.getPipeProcess();
				pw.attachProcess(mkfifo_process);

				// It can take a long time for Windows to create a named pipe (and
				// mkfifo can be slow if /tmp isn't memory-mapped), so run this in
				// the current thread.
				mkfifo_process.runInSameThread();

				pipe.deleteLater();
			}
		}

		pw.runInNewThread();

		try {
			Thread.sleep(100);
		} catch (InterruptedException e) { }

		return pw;
	}

	@Override
	public String mimeType() {
		return HTTPResource.VIDEO_TRANSCODE;
	}

	@Override
	public String name() {
		return "MEncoder Video";
	}

	@Override
	public int type() {
		return Format.VIDEO;
	}

	private String[] getSpecificCodecOptions(
		String codecParam,
		DLNAMediaInfo media,
		OutputParams params,
		String filename,
		String externalSubtitlesFileName,
		boolean enable,
		boolean verifyOnly
	) {
		StringBuilder sb = new StringBuilder();
		String codecs = enable ? DEFAULT_CODEC_CONF_SCRIPT : "";
		codecs += "\n" + codecParam;
		StringTokenizer stLines = new StringTokenizer(codecs, "\n");

		try {
			Interpreter interpreter = new Interpreter();
			interpreter.setStrictJava(true);
			ArrayList<String> types = CodecUtil.getPossibleCodecs();
			int rank = 1;

			if (types != null) {
				for (String type : types) {
					int r = rank++;
					interpreter.set("" + type, r);
					String secondaryType = "dummy";

					if ("matroska".equals(type)) {
						secondaryType = "mkv";
						interpreter.set(secondaryType, r);
					} else if ("rm".equals(type)) {
						secondaryType = "rmvb";
						interpreter.set(secondaryType, r);
					} else if ("mpeg2video".equals(type)) {
						secondaryType = "mpeg2";
						interpreter.set(secondaryType, r);
					} else if ("mpeg1video".equals(type)) {
						secondaryType = "mpeg1";
						interpreter.set(secondaryType, r);
					}

					if (media.getContainer() != null && (media.getContainer().equals(type) || media.getContainer().equals(secondaryType))) {
						interpreter.set("container", r);
					} else if (media.getCodecV() != null && (media.getCodecV().equals(type) || media.getCodecV().equals(secondaryType))) {
						interpreter.set("vcodec", r);
					} else if (params.aid != null && params.aid.getCodecA() != null && params.aid.getCodecA().equals(type)) {
						interpreter.set("acodec", r);
					}
				}
			} else {
				return null;
			}

			interpreter.set("filename", filename);
			interpreter.set("audio", params.aid != null);
			interpreter.set("subtitles", params.sid != null);
			interpreter.set("srtfile", externalSubtitlesFileName);

			if (params.aid != null) {
				interpreter.set("samplerate", params.aid.getSampleRate());
			}

			String framerate = media.getValidFps(false);

			try {
				if (framerate != null) {
					interpreter.set("framerate", Double.parseDouble(framerate));
				}
			} catch (NumberFormatException e) {
				logger.debug("Could not parse framerate from \"" + framerate + "\"");
			}

			interpreter.set("duration", media.getDurationInSeconds());

			if (params.aid != null) {
				interpreter.set("channels", params.aid.getAudioProperties().getNumberOfChannels());
			}

			interpreter.set("height", media.getHeight());
			interpreter.set("width", media.getWidth());

			while (stLines.hasMoreTokens()) {
				String line = stLines.nextToken();

				if (!line.startsWith("#") && line.trim().length() > 0) {
					int separator = line.indexOf("::");

					if (separator > -1) {
						String key = null;

						try {
							key = line.substring(0, separator).trim();
							String value = line.substring(separator + 2).trim();

							if (value.length() > 0) {
								if (key.length() == 0) {
									key = "1 == 1";
								}

								Object result = interpreter.eval(key);

								if (result != null && result instanceof Boolean && (Boolean) result) {
									sb.append(" ");
									sb.append(value);
								}
							}
						} catch (Throwable e) {
							logger.debug("Error while executing: " + key + " : " + e.getMessage());

							if (verifyOnly) {
								return new String[]{"@@Error while parsing: " + e.getMessage()};
							}
						}
					} else if (verifyOnly) {
						return new String[]{"@@Malformatted line: " + line};
					}
				}
			}
		} catch (EvalError e) {
			logger.debug("BeanShell error: " + e.getMessage());
		}

		String completeLine = sb.toString();
		ArrayList<String> args = new ArrayList<String>();
		StringTokenizer st = new StringTokenizer(completeLine, " ");

		while (st.hasMoreTokens()) {
			String arg = st.nextToken().trim();

			if (arg.length() > 0) {
				args.add(arg);
			}
		}

		String definitiveArgs[] = new String[args.size()];
		args.toArray(definitiveArgs);

		return definitiveArgs;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isCompatible(DLNAResource resource) {
		return PlayerUtil.isVideo(resource, Format.Identifier.ISO)
			|| PlayerUtil.isVideo(resource, Format.Identifier.MKV)
			|| PlayerUtil.isVideo(resource, Format.Identifier.MPG);
	}
}