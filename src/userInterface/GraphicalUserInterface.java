/*
 * Created by Chief on Thu Nov 15 10:05:17 CST 2018
 */

package userInterface;

import component.Lexical;
import component.MidiPlayer;
import component.Semantic;
import component.Syntactic;
import entity.interpreter.Node;
import entity.interpreter.Token;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Chief
 */

public class GraphicalUserInterface extends JFrame {

    private File midiFile;
    private File tempMidiFile;
    private File file;

    private boolean hasSaved = false;
    private boolean hasChanged = false;
    private boolean isLoadedMidiFile = false;
    private boolean canProcessDocument = true;

    private SimpleAttributeSet attributeSet;
    private SimpleAttributeSet statementAttributeSet;
    private SimpleAttributeSet durationAttributeSet;
    private SimpleAttributeSet normalAttributeSet;
    private SimpleAttributeSet commentAttributeSet;
    private SimpleAttributeSet errorAttributeSet;
    private SimpleAttributeSet sameTimeNoteAttributeSet;

    private Pattern statementPattern;
    private Pattern keywordPattern;
    private Pattern parenPattern;
    private Pattern sameNotePattern;

    private Lexical lexical;
    private Syntactic syntactic;
    private Semantic semantic;
    private MidiPlayer midiPlayer;

    private class InputStatus {
        int caretPos;
        String inputString;
    }

    private List<InputStatus> inputStatusList;
    private int inputStatusListMax;
    private int inputStatusListIndex;

    private java.util.Timer timer;

    private class MyDocument extends DefaultStyledDocument {
        @Override
        public void insertString(int offs, String str, AttributeSet a) throws BadLocationException {
            if (canProcessDocument) {
                boolean isComment = false;
                boolean isAutoComplete = false;
                //处理按键映射
                if (str.equals("0") || str.equals("1") || str.equals("2") || str.equals("3") || str.equals("4") ||
                        str.equals("5") || str.equals("6") || str.equals("7") || str.equals("8") || str.equals("9")) {
                    str = noteMapping[Integer.parseInt(str)];
                }

                //处理自动补全
                else {
                    String text = inputTextPane.getText().replace("\r", "");
                    char b;
                    if (offs == text.length() || (b = text.charAt(offs)) == '\n' || b == ' ' || b == ')' || b == ']' || b == '|' || (offs > 0 && text.charAt(offs - 1) == '/')) {
                        switch (str) {
                            case "(":
                                isAutoComplete = true;
                                str += ")";
                                break;
                            case "[":
                                isAutoComplete = true;
                                str += "]";
                                break;
                            case "{":
                                isAutoComplete = true;
                                str += "}";
                                break;
                            case "<":
                                isAutoComplete = true;
                                str += ">";
                                break;
                            case "|":
                                isAutoComplete = true;
                                str += "|";
                                break;
                            case "*":
                                str += "\n\n*/";
                                isComment = true;
                                break;
                        }
                    }

                    if (offs < text.length() && ((b = text.charAt(offs)) == ')' && str.equals(")") || str.equals("]") && b == ']' || str.equals("}") && b == '}' || str.equals(">") && b == '>' || str.equals("|") && b == '|')) {
                        str = "";
                        isAutoComplete = true;
                    }
                }

                saveInputStatus();

                super.insertString(offs, str, a);

                if (isAutoComplete)
                    inputTextPane.setCaretPosition(offs + 1);
                if (isComment)
                    inputTextPane.setCaretPosition(offs + 2);

                contentChanged();
            } else
                super.insertString(offs, str, a);
        }

        @Override
        public void remove(int offs, int len) throws BadLocationException {
            if (canProcessDocument) {
                //自动删除界符
                if (offs < inputTextPane.getText().replace("\r", "").length() - 1) {
                    char a = inputTextPane.getText().replace("\r", "").charAt(offs);
                    char b = inputTextPane.getText().replace("\r", "").charAt(offs + 1);
                    if ((a == '(' && b == ')') || (a == '[' && b == ']') || (a == '{' && b == '}') || (a == '<' && b == '>') || (a == '|' && b == '|')) {
                        len++;
                    }
                }

                saveInputStatus();
                super.remove(offs, len);
                contentChanged();
            } else
                super.remove(offs, len);
        }
    }

    private MyDocument inputStyledDocument;

    private String[] noteMapping;
    private boolean isNoteMappingPageOpen;

    //初始化与案件绑定
    public GraphicalUserInterface() {
        initComponents();
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        //样式
        attributeSet = new SimpleAttributeSet();
        statementAttributeSet = new SimpleAttributeSet();
        durationAttributeSet = new SimpleAttributeSet();
        normalAttributeSet = new SimpleAttributeSet();
        commentAttributeSet = new SimpleAttributeSet();
        errorAttributeSet = new SimpleAttributeSet();
        sameTimeNoteAttributeSet = new SimpleAttributeSet();

        StyleConstants.setForeground(attributeSet, new Color(92, 101, 192));
        StyleConstants.setBold(attributeSet, true);
        StyleConstants.setForeground(statementAttributeSet, new Color(30, 80, 180));
        StyleConstants.setBold(statementAttributeSet, true);
        StyleConstants.setForeground(durationAttributeSet, new Color(111, 150, 255));
        StyleConstants.setForeground(commentAttributeSet, new Color(128, 128, 128));
        StyleConstants.setForeground(errorAttributeSet, new Color(238, 0, 1));
        StyleConstants.setBackground(sameTimeNoteAttributeSet, new Color(245, 248, 255));
        inputStyledDocument = new MyDocument();
        inputTextPane.setDocument(inputStyledDocument);
        statementPattern = Pattern.compile("\\bparagraph\\b|\\bend\\b|\\bplay");
        keywordPattern = Pattern.compile("\\bspeed=|\\binstrument=|\\bvolume=|\\b1=");
        parenPattern = Pattern.compile("<(\\s*\\{?\\s*(1|2|4|8|g|w|\\*)+\\s*\\}?\\s*)+>");
        sameNotePattern = Pattern.compile("\\|");

        //输入内容数组
        inputStatusList = new ArrayList<>();
        inputStatusListMax = -1;
        inputStatusListIndex = 0;

        //数字键映射
        noteMapping = new String[]{"0", "1", "2", "3", "4", "5", "6", "7", "8", "9"};
        isNoteMappingPageOpen = false;

        //关闭窗口提示
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                //删除临时Midi文件
                exitMenuItemActionPerformed(null);
            }
        });

        //按键映射保存按键的监听
        outputTextPane.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (isNoteMappingPageOpen && e.getKeyCode() == KeyEvent.VK_S && e.isControlDown())
                    saveNoteMapping();
            }
        });

        //着色与补全、快捷键等的监听
        inputTextPane.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_RIGHT)
                    forwardMenuItemActionPerformed(null);

                if (e.getKeyCode() == KeyEvent.VK_LEFT)
                    backwardMenuItemActionPerformed(null);

                if (e.getKeyCode() == KeyEvent.VK_RIGHT && e.isControlDown())
                    fastForwardMenuItemActionPerformed(null);

                if (e.getKeyCode() == KeyEvent.VK_LEFT && e.isControlDown())
                    fastBackwardMenuItemActionPerformed(null);

                if (e.getKeyCode() == KeyEvent.VK_S && e.isControlDown())
                    saveMenuItemActionPerformed(null);

                if (e.getKeyCode() == KeyEvent.VK_Z && e.isControlDown()) {
                    canProcessDocument = false;
                    undo();
                }

                if (e.getKeyCode() == KeyEvent.VK_Y && e.isControlDown()) {
                    canProcessDocument = false;
                    redo();
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                if (e.isControlDown() && (e.getKeyCode() == KeyEvent.VK_Z || e.getKeyCode() == KeyEvent.VK_Y))
                    canProcessDocument = true;
            }
        });

        //组件实例化
        lexical = new Lexical();
        syntactic = new Syntactic();
        semantic = new Semantic();
        midiPlayer = new MidiPlayer();

        //行号与滚动条
        scrollPane3.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        scrollPane3.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        StringBuilder lineStr = new StringBuilder();
        for (int i = 1; i < 1000; i++)
            lineStr.append(i).append("\n");
        lineTextArea.setText(lineStr.toString());
        scrollPane1.getVerticalScrollBar().addAdjustmentListener(e -> scrollPane3.getVerticalScrollBar().setValue(scrollPane1.getVerticalScrollBar().getValue()));

        tipsMenuItemActionPerformed(null);

        //播放完成事件
        midiPlayer.getSequencer().addMetaEventListener(meta -> {
            if (meta.getType() == 47) {
                stopDirectMenuItemActionPerformed(null);
            }
        });
    }

    //将输入状态入栈
    private void saveInputStatus() {
        InputStatus inputStatus = new InputStatus();
        inputStatus.caretPos = inputTextPane.getCaretPosition();
        inputStatus.inputString = inputTextPane.getText();
        inputStatusList.add(inputStatusListIndex, inputStatus);
        inputStatusListMax = inputStatusListIndex;
        inputStatusListIndex++;
    }

    //撤销(undo)
    private void undo() {
        if (inputStatusListIndex == inputStatusListMax + 1) {
//            if (!inputStatusList.get(inputStatusListIndex - 1).inputString.equals(inputStatusList.get(inputStatusListIndex - 2).inputString)) {
            saveInputStatus();
            inputStatusListIndex--;
        }

        if (inputStatusListIndex > 0) {
            InputStatus inputStatus = inputStatusList.get(--inputStatusListIndex);
            inputTextPane.setText(inputStatus.inputString);
            inputTextPane.setCaretPosition(inputStatus.caretPos);
            contentChanged();
        }
    }

    //重做(redo)
    private void redo() {
        if (inputStatusListIndex < inputStatusListMax) {
            InputStatus inputStatus = inputStatusList.get(++inputStatusListIndex);
            inputTextPane.setText(inputStatus.inputString);
            inputTextPane.setCaretPosition(inputStatus.caretPos);
            contentChanged();
        }
    }

    //内容变动调用的函数
    private void contentChanged() {
        refreshColor();

        if (hasChanged)
            return;

        hasChanged = true;
        if (this.getTitle().lastIndexOf("(Unsaved)") == -1)
            this.setTitle(this.getTitle() + " (Unsaved)");
    }

    //内容变动之后是否保存
    private boolean showSaveComfirm(String confirm) {
        if (hasChanged) {
            int exit = JOptionPane.showConfirmDialog(null, confirm, "Confirm", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
            switch (exit) {
                case JOptionPane.YES_OPTION:
                    saveMenuItemActionPerformed(null);
                    break;
                case JOptionPane.NO_OPTION:
                    break;
                case JOptionPane.CANCEL_OPTION:
                    return false;
            }
        }
        return true;
    }

    //代码着色
    private void refreshColor() {
        String input = inputTextPane.getText().replace("\r", "");

        inputStyledDocument.setCharacterAttributes(
                0,
                input.length(),
                normalAttributeSet, true
        );

        //声明着色
        Matcher statementMatcher = statementPattern.matcher(input);
        while (statementMatcher.find()) {
            inputStyledDocument.setCharacterAttributes(
                    statementMatcher.start(),
                    statementMatcher.end() - statementMatcher.start(),
                    statementAttributeSet, true
            );
        }

        //关键字着色
        Matcher inputMatcher = keywordPattern.matcher(input);
        while (inputMatcher.find()) {
            inputStyledDocument.setCharacterAttributes(
                    inputMatcher.start(),
                    inputMatcher.end() - inputMatcher.start(),
                    attributeSet, true
            );
        }

        //节奏片段着色
        Matcher parenMatcher = parenPattern.matcher(input);
        while (parenMatcher.find()) {
            inputStyledDocument.setCharacterAttributes(
                    parenMatcher.start(),
                    parenMatcher.end() - parenMatcher.start(),
                    durationAttributeSet, true
            );
        }

        //注释着色
        for (int i = 0; i < input.length(); i++) {
            //单行注释
            if (i + 1 < input.length())
                if (input.charAt(i) == '/' && input.charAt(i + 1) == '/')
                    while (i + 1 < input.length() && input.charAt(i) != '\n') {
                        i++;
                        inputStyledDocument.setCharacterAttributes(
                                i - 1,
                                2,
                                commentAttributeSet, true
                        );
                    }

            //多行注释
            if (i + 1 < input.length() && input.charAt(i) == '/' && input.charAt(i + 1) == '*')
                while (i + 1 < input.length() && (input.charAt(i) != '*' || input.charAt(i + 1) != '/')) {
                    i++;
                    inputStyledDocument.setCharacterAttributes(
                            i - 1,
                            3,
                            commentAttributeSet, true
                    );
                }
        }

        //同时音着色
        int count = 0;
        int last = 0;
        Matcher noteMatcher = sameNotePattern.matcher(input);
        while (noteMatcher.find()) {
            count++;

            if (count % 2 == 0) {
                inputStyledDocument.setCharacterAttributes(
                        last,
                        noteMatcher.end() - last,
                        sameTimeNoteAttributeSet, true
                );
            } else
                last = noteMatcher.start();
        }
    }

    //新建空文件
    private void newEmptyMenuItemActionPerformed(ActionEvent e) {
        if (showSaveComfirm("Exist unsaved content, save before new file?")) {
            hasSaved = false;

            inputTextPane.setText("");
            outputTextPane.setText("");
            hasChanged = false;
            isLoadedMidiFile = false;
            this.setTitle("Euterpe - New Empty File");
        }
    }

    //新建模板文件
    private void newMenuItemActionPerformed(ActionEvent e) {
        if (showSaveComfirm("Exist unsaved content, save before new file?")) {
            hasSaved = false;

            String str = "/*\n" +
                    " 数字乐谱模板\n" +
                    " 声部1 + 声部2\n" +
                    " 双声部 Version\n" +
                    " */\n" +
                    "\n" +
                    "//声部1\n" +
                    "paragraph Name1\n" +
                    "instrument= 0\n" +
                    "volume= 127\n" +
                    "speed= 90\n" +
                    "1= C\n" +
                    "1234 567[1]  <4444 4444>\n" +
                    "[1]765 4321  <4444 4444>\n" +
                    "\n" +
                    "1324    3546  <8888 8888>\n" +
                    "576[1] 7[2]1  <8888 884>\n" +
                    "\n" +
                    "[1]675 6453  <gggg gggg>\n" +
                    "4231   2(7)1  <gggg gg8>\n" +
                    "end\n" +
                    "\n" +
                    "//声部2\n" +
                    "paragraph Name2\n" +
                    "instrument= 0\n" +
                    "volume= 127\n" +
                    "speed= 90\n" +
                    "1= C\n" +
                    "1234 567[1]  <4444 4444>\n" +
                    "[1]765 4321  <4444 4444>\n" +
                    "\n" +
                    "1324    3546  <8888 8888>\n" +
                    "576[1] 7[2]1  <8888 884>\n" +
                    "\n" +
                    "[1]675 6453  <gggg gggg>\n" +
                    "4231   2(7)1  <gggg gg8>\n" +
                    "end\n" +
                    "\n" +
                    "//添加更多声部......\n" +
                    "\n" +
                    "//多声部同时播放\n" +
                    "play(Name1&Name2)";
            inputTextPane.setText(str);
            inputTextPane.setCaretPosition(0);

            outputTextPane.setText("");
            hasChanged = false;
            isLoadedMidiFile = false;
            this.setTitle("Euterpe - New Template File");
        }
    }

    //打开文件
    private void openMenuItemActionPerformed(ActionEvent e) {
        if (!showSaveComfirm("Exist unsaved content, save before open file?"))
            return;

        canProcessDocument = false;
        saveInputStatus();

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        FileNameExtensionFilter filter = new FileNameExtensionFilter("Euterpe File", "mui");
        fileChooser.setFileFilter(filter);
        int value = fileChooser.showOpenDialog(this);
        if (value == JFileChooser.CANCEL_OPTION)
            return;
        file = fileChooser.getSelectedFile();
        try {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
            StringBuilder stringBuilder = new StringBuilder();
            String content;
            while ((content = bufferedReader.readLine()) != null) {
                stringBuilder.append(content);
                stringBuilder.append(System.getProperty("line.separator"));
            }
            bufferedReader.close();

            inputTextPane.setText(stringBuilder.toString());
            inputTextPane.setCaretPosition(0);
            outputTextPane.setText("");
            hasSaved = true;
            hasChanged = false;
            stopDirectMenuItemActionPerformed(null);
            isLoadedMidiFile = false;
            this.setTitle("Euterpe - " + file.getName());
            canProcessDocument = true;
            contentChanged();
        } catch (IOException e1) {
//            e1.printStackTrace();
        }
    }

    //保存文件
    private void saveMenuItemActionPerformed(ActionEvent e) {
        if (!hasSaved) {
            saveAsMenuItemActionPerformed(null);
        } else {
            try {
                if (!file.exists())
                    file.createNewFile();
                BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8));
                bufferedWriter.write(inputTextPane.getText());
                bufferedWriter.close();
                hasChanged = false;
                isLoadedMidiFile = false;
                this.setTitle("Euterpe - " + file.getName());
                stopDirectMenuItemActionPerformed(null);
                isLoadedMidiFile = false;
            } catch (IOException e1) {
//                e1.printStackTrace();
            }
        }
    }

    //另存为文件
    private void saveAsMenuItemActionPerformed(ActionEvent e) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        FileNameExtensionFilter filter = new FileNameExtensionFilter("Euterpe File", "mui");
        fileChooser.setFileFilter(filter);
        int value = fileChooser.showSaveDialog(this);
        if (value == JFileChooser.CANCEL_OPTION)
            return;
        String fileStr = fileChooser.getSelectedFile().getAbsoluteFile().toString();
        if (fileStr.lastIndexOf(".mui") == -1)
            fileStr += ".mui";
        file = new File(fileStr);
        try {
            if (!file.exists())
                file.createNewFile();
            BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8));
            bufferedWriter.write(inputTextPane.getText());
            bufferedWriter.close();
            hasSaved = true;
            hasChanged = false;
            isLoadedMidiFile = false;
            this.setTitle("Euterpe - " + file.getName());
            stopDirectMenuItemActionPerformed(null);
            isLoadedMidiFile = false;
        } catch (IOException e1) {
//            e1.printStackTrace();
        }
    }

    //通过行号找到该行第一个字符在输入字符串中的位置
    private int getIndexByLine(int line) {
        int index = 0;
        String input = inputTextPane.getText().replace("\r", "") + "\n";

        for (int i = 0; i < line - 1; i++) {
            index = input.indexOf("\n", index + 1);
        }
        return index;
    }

    //词法分析
    private List<Token> runLex(String input, StringBuilder output) {
        lexical.Lex(input);
        List<Token> tokens = lexical.getTokens();

        if (lexical.getError()) {
            output.append(lexical.getErrorInfo(tokens));
            output.append("\n检测到错误");
            outputTextPane.setText(output.toString());
            for (int line : lexical.getErrorLine()) {
                inputStyledDocument.setCharacterAttributes(
                        getIndexByLine(line),
                        getIndexByLine(line + 1) - getIndexByLine(line),
                        errorAttributeSet, true
                );
            }
            return null;
        }

        return tokens;
    }

    //语法分析
    private Node runSyn(List<Token> tokens, StringBuilder output) {
        Node AbstractSyntaxTree = syntactic.Parse(tokens);

        if (syntactic.getIsError()) {
            output.append(syntactic.getErrors(AbstractSyntaxTree));
            output.append("\n检测到错误");
            outputTextPane.setText(output.toString());
            for (int line : syntactic.getErrorList()) {
                inputStyledDocument.setCharacterAttributes(
                        getIndexByLine(line),
                        getIndexByLine(line + 1) - getIndexByLine(line),
                        errorAttributeSet, true
                );
            }
            return null;
        }

        return AbstractSyntaxTree;
    }

    //语义分析
    private String runMidiSem(Node abstractSyntaxTree, StringBuilder output) {
        String code = semantic.interpret(abstractSyntaxTree);

        if (semantic.getIsError()) {
            output.append(semantic.getErrors());
            output.append("\n检测到错误");
            outputTextPane.setText(output.toString());
            for (int line : semantic.getErrorLines()) {
                inputStyledDocument.setCharacterAttributes(
                        getIndexByLine(line),
                        getIndexByLine(line + 1) - getIndexByLine(line),
                        errorAttributeSet, true
                );
            }
            return null;
        } else {
            output.append(code);
        }

        return code;
    }

    //执行解释过程
    private boolean runInterpret() {
        StringBuilder stringBuilder = new StringBuilder();

        if (inputTextPane.getText().isEmpty())
            return false;

        isNoteMappingPageOpen = false;
        setOutputTextPaneEditable(false);

        List<Token> tokens = runLex(inputTextPane.getText(), stringBuilder);

        if (tokens == null)
            return false;

        Node AbstractSyntaxTree = runSyn(tokens, stringBuilder);

        if (AbstractSyntaxTree == null)
            return false;

        String code = runMidiSem(AbstractSyntaxTree, stringBuilder);

        if (code == null)
            return false;

        outputTextPane.setText(code + "\n\n===========================================\nMidi Successfully Generated");

        return true;
    }

    //导出Midi文件
    private void generateMidiMenuItemActionPerformed(ActionEvent e) {
        if (!runInterpret())
            return;

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        FileNameExtensionFilter filter = new FileNameExtensionFilter("Midi File", "mid");
        fileChooser.setFileFilter(filter);
        int value = fileChooser.showSaveDialog(this);
        if (value == JFileChooser.CANCEL_OPTION)
            return;
        String fileStr = fileChooser.getSelectedFile().getAbsoluteFile().toString();
        if (fileStr.lastIndexOf(".mid") == -1)
            fileStr += ".mid";
        midiFile = new File(fileStr);

        if (!semantic.getMidiFile().writeToFile(midiFile))
            JOptionPane.showMessageDialog(this, "目标文件被占用，无法导出", "Warning", JOptionPane.INFORMATION_MESSAGE);

    }

    //生成临时Midi文件
    private boolean generateTempMidiFile() {
        if (!runInterpret())
            return false;

        if (tempMidiFile == null) {
            tempMidiFile = new File("tempMidi.mid");
        }

        if (!semantic.getMidiFile().writeToFile(tempMidiFile)) {
            JOptionPane.showMessageDialog(this, "目标文件被占用，无法导出", "Warning", JOptionPane.INFORMATION_MESSAGE);
            return false;
        }

        return true;
    }

    //直接播放Midi文件
    private void playMenuItemActionPerformed(ActionEvent e) {
        if (!generateTempMidiFile())
            return;

        try {
            Runtime.getRuntime().exec("rundll32 url.dll FileProtocolHandler file://" + tempMidiFile.getAbsolutePath().replace("\\", "\\\\"));
        } catch (IOException e1) {
            e1.printStackTrace();
        }
    }

    //禁用使用右侧输出内容的按钮
    private void forbidOutputButton(boolean flag) {
        instruMenuItem.setEnabled(!flag);
        tipsMenuItem.setEnabled(!flag);
        aboutMenuItem.setEnabled(!flag);
        demoMenuItem.setEnabled(!flag);
        setNoteMappingMenuItem.setEnabled(!flag);
    }

    //读取SoundFont
    private void loadSoundFontMenuItemActionPerformed(ActionEvent e) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        FileNameExtensionFilter filter = new FileNameExtensionFilter("SoundFont File", "sf2", "sf3");
        fileChooser.setFileFilter(filter);
        int value = fileChooser.showOpenDialog(this);
        if (value == JFileChooser.CANCEL_OPTION)
            return;
        File soundFontFile = fileChooser.getSelectedFile();
        midiPlayer.loadSoundBank(soundFontFile);
    }

    //直接播放Midi按钮
    private void playDirectMenuItemActionPerformed(ActionEvent e) {
        if (!isLoadedMidiFile) {
            if (!generateTempMidiFile())
                return;

            midiPlayer.loadMidiFile(tempMidiFile);
            isLoadedMidiFile = true;
        }

        if (midiPlayer.getSequencer().isRunning()) {
            midiPlayer.pause();
            playDirectMenuItem.setText("Resume");
            forbidOutputButton(false);
        } else {
            midiPlayer.play();
            playDirectMenuItem.setText("Pause");
            forbidOutputButton(true);
            isNoteMappingPageOpen = false;
            setOutputTextPaneEditable(false);
        }

        if (timer == null)
            timer = new Timer();

        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (midiPlayer.getSequencer().isRunning()) {
                    if (hasSaved) {
                        midiPlayer.setTitle(file.getName().substring(0, file.getName().indexOf(".mui")));
                        outputTextPane.setText(midiPlayer.getGraphicPlayer());
                    } else {
                        midiPlayer.setTitle("Untitled Song");
                        outputTextPane.setText(midiPlayer.getGraphicPlayer());
                    }
                }
            }
        }, 0, 100);
    }

    //停止直接播放Midi按钮
    private void stopDirectMenuItemActionPerformed(ActionEvent e) {
        if (timer != null) {
            timer.cancel();
            timer.purge();
            timer = null;
        }

        playDirectMenuItem.setText("Play");
        midiPlayer.stop();
        isLoadedMidiFile = false;
        forbidOutputButton(false);
    }

    //快进
    private void fastMove(int seconds) {
        if (midiPlayer.getSequencer().isRunning()) {
            long pos = midiPlayer.getSequencer().getMicrosecondPosition();
            pos += 1000000 * seconds;
            midiPlayer.getSequencer().setMicrosecondPosition(pos);
        }
    }

    //前进1秒
    private void forwardMenuItemActionPerformed(ActionEvent e) {
        fastMove(1);
    }

    //后退1秒
    private void backwardMenuItemActionPerformed(ActionEvent e) {
        fastMove(-1);

    }

    //前进五秒
    private void fastForwardMenuItemActionPerformed(ActionEvent e) {
        fastMove(5);
    }

    //后退五秒
    private void fastBackwardMenuItemActionPerformed(ActionEvent e) {
        fastMove(-5);
    }

    //设置输出面板是否可编辑
    private void setOutputTextPaneEditable(Boolean flag) {
        outputTextPane.setEditable(flag);

        if (flag) {
            outputTextPane.setSelectedTextColor(Color.white);
            outputTextPane.setSelectionColor(new Color(26, 125, 196));
        } else {
            outputTextPane.setSelectedTextColor(new Color(60, 60, 60));
            outputTextPane.setSelectionColor(Color.white);
        }
    }

    //设置按键映射
    private void setNoteMappingMenuItemActionPerformed(ActionEvent e) {
        isNoteMappingPageOpen = true;
        setOutputTextPaneEditable(true);

        String str = "============================================\n" +
                " \t\t  Set Note Mapping\n" +
                "-------------------------------------------------------------------------\n" +
                "* 此处用于设置数字键与音符的映射关系\n" +
                "\n" +
                "1.设置说明：\n" +
                "\t1）需要修改的内容为“->”符号之后的内容\n" +
                "\t2）理论上可以修改为任意值，长度不固定，数字字母皆可\n" +
                "\t3）请保持此页面格式进行修改，不能删除空格，如1->#1，\n" +
                "\t4）上一条示例意为按下数字键1输入#1，方便扒谱处理调性\n" +
                "\t5）设置完成之后请在此文本框内使用Ctrl+S保存设置\n" +
                "--------------------------------------------------------------------------\n" +
                "\t0 -> " + noteMapping[0] + "\n" +
                "\n" +
                "\t1 -> " + noteMapping[1] + "\n" +
                "\n" +
                "\t2 -> " + noteMapping[2] + "\n" +
                "\n" +
                "\t3 -> " + noteMapping[3] + "\n" +
                "\n" +
                "\t4 -> " + noteMapping[4] + "\n" +
                "\n" +
                "\t5 -> " + noteMapping[5] + "\n" +
                "\n" +
                "\t6 -> " + noteMapping[6] + "\n" +
                "\n" +
                "\t7 -> " + noteMapping[7] + "\n" +
                "\n" +
                "\t8 -> " + noteMapping[8] + "\n" +
                "\n" +
                "\t9 -> " + noteMapping[9] + "\n" +
                "============================================";

        outputTextPane.setText(str);
        outputTextPane.setCaretPosition(0);
    }

    //保存按键映射
    private void saveNoteMapping() {
        String str = outputTextPane.getText();

        Pattern pattern = Pattern.compile("\\d -> \\S+");

        Matcher matcher = pattern.matcher(str);

        int index = 0;
        String[] tempNoteMapping = new String[10];

        while (matcher.find()) {
            String temp = matcher.group();
            if (index < 10 && Integer.parseInt(temp.substring(0, 1)) == index) {
                tempNoteMapping[index] = temp.replace(" ", "").substring(3);
                index++;
            } else
                break;
        }

        String out;

        if (index != 10) {
            out = "============================================\n" +
                    " \t\t  Set Note Mapping\n" +
                    "-------------------------------------------------------------------------\n" +
                    "* !!发现格式错误，请保持此页面格式进行修改!!\n" +
                    "\n" +
                    "1.设置说明：\n" +
                    "\t1）需要修改的内容为“->”符号之后的内容\n" +
                    "\t2）理论上可以修改为任意值，长度不固定，数字字母皆可\n" +
                    "\t3）请保持此页面格式进行修改，不能删除空格，如1->#1，\n" +
                    "\t4）上一条示例意为按下数字键1输入#1，方便扒谱处理调性\n" +
                    "\t5）设置完成之后请在此文本框内使用Ctrl+S保存设置\n" +
                    "--------------------------------------------------------------------------\n" +
                    "\t0 -> " + noteMapping[0] + "\n" +
                    "\n" +
                    "\t1 -> " + noteMapping[1] + "\n" +
                    "\n" +
                    "\t2 -> " + noteMapping[2] + "\n" +
                    "\n" +
                    "\t3 -> " + noteMapping[3] + "\n" +
                    "\n" +
                    "\t4 -> " + noteMapping[4] + "\n" +
                    "\n" +
                    "\t5 -> " + noteMapping[5] + "\n" +
                    "\n" +
                    "\t6 -> " + noteMapping[6] + "\n" +
                    "\n" +
                    "\t7 -> " + noteMapping[7] + "\n" +
                    "\n" +
                    "\t8 -> " + noteMapping[8] + "\n" +
                    "\n" +
                    "\t9 -> " + noteMapping[9] + "\n" +
                    "============================================";
        } else {
            noteMapping = tempNoteMapping;
            out = "============================================\n" +
                    " \t\t  Set Note Mapping\n" +
                    "-------------------------------------------------------------------------\n" +
                    "* 此处用于设置数字键与音符的映射关系（已保存）\n" +
                    "\n" +
                    "1.设置说明：\n" +
                    "\t1）需要修改的内容为“->”符号之后的内容\n" +
                    "\t2）理论上可以修改为任意值，长度不固定，数字字母皆可\n" +
                    "\t3）请保持此页面格式进行修改，不能删除空格，如1->#1，\n" +
                    "\t4）上一条示例意为按下数字键1输入#1，方便扒谱处理调性\n" +
                    "\t5）设置完成之后请在此文本框内使用Ctrl+S保存设置\n" +
                    "--------------------------------------------------------------------------\n" +
                    "\t0 -> " + noteMapping[0] + "\n" +
                    "\n" +
                    "\t1 -> " + noteMapping[1] + "\n" +
                    "\n" +
                    "\t2 -> " + noteMapping[2] + "\n" +
                    "\n" +
                    "\t3 -> " + noteMapping[3] + "\n" +
                    "\n" +
                    "\t4 -> " + noteMapping[4] + "\n" +
                    "\n" +
                    "\t5 -> " + noteMapping[5] + "\n" +
                    "\n" +
                    "\t6 -> " + noteMapping[6] + "\n" +
                    "\n" +
                    "\t7 -> " + noteMapping[7] + "\n" +
                    "\n" +
                    "\t8 -> " + noteMapping[8] + "\n" +
                    "\n" +
                    "\t9 -> " + noteMapping[9] + "\n" +
                    "============================================";
        }

        outputTextPane.setText(out);
        outputTextPane.setCaretPosition(0);
    }

    //关于
    private void aboutMenuItemActionPerformed(ActionEvent e) {
        isNoteMappingPageOpen = false;
        setOutputTextPaneEditable(false);

        String str = "============================================\n" +
                "\t\t          Euterpe\n" +
                "--------------------------------------------------------------------------\n" +
                "* 名称来源于希腊神话中司管抒情诗与音乐的缪斯——欧忒耳佩\n" +
                "   意为“令人快乐”（原Music Interpreter）\n" +
                "\n" +
                "1.简介\n" +
                "\t通过设计原创的音乐语言，运用解释器原理设计\n" +
                "\t以便于键盘输入为特点的，数字乐谱——Midi解释器\n" +
                "\t包含完整的词法分析、语法分析与语义分析\n" +
                "\t可通过内置人机良好的数字乐谱编辑器谱写乐谱\n" +
                "\t并通过内置实时Midi播放器，加载SoundFont2音源播放\n" +
                "\t同时包含便于扒谱的工具与生成Midi文件等功能\n" +
                "\n" +
                "2.项目成员\n" +
                "\t1）项目组长，语义分析，用户界面：Chief\n" +
                "\t2）词法分析，Midi转Mui：yyzih\n" +
                "\t3）语法分析，语言设计：AsrielMao\n" +
                "\n" +
                "3.当前版本\n" +
                "\t4.0.0 Alpha\n" +
                "\n" +
                "\t\t\t\t   All Rights Reserved. \n" +
                "    \t    Copyright © 2018-2020 Chief, yyzih and AsrielMao.\n" +
                "============================================";
        outputTextPane.setText(str);
        outputTextPane.setCaretPosition(0);
    }

    //展示Demo
    private void demoMenuItemActionPerformed(ActionEvent e) {
        if (showSaveComfirm("Exist unsaved content, save before open the demo?")) {
            hasSaved = false;

            String str = "/*\n" +
                    " 欢乐颂\n" +
                    " 女高音 + 女中音\n" +
                    " 双声部 Version\n" +
                    " */\n" +
                    "\n" +
                    "//女高音\n" +
                    "paragraph soprano\n" +
                    "instrument= 0\n" +
                    "volume= 127\n" +
                    "speed= 140\n" +
                    "1= D\n" +
                    "3345 5432 <4444 4444>\n" +
                    "1123 322 <4444 4*82>\n" +
                    "3345 5432 <4444 4444>\n" +
                    "1123 211 <4444 4*82>\n" +
                    "2231 23431 <4444 4{88}44>\n" +
                    "23432 12(5) <4{88}44 {44}4>\n" +
                    "33345 54342 <{44}444 44{48}8>\n" +
                    "1123 211 <4444 4*82>\n" +
                    "end\n" +
                    "\n" +
                    "//女中音\n" +
                    "paragraph alto\n" +
                    "instrument= 0\n" +
                    "volume= 110\n" +
                    "speed= 140\n" +
                    "1= D\n" +
                    "1123 321(5) <4444 4444>\n" +
                    "(3555) 1(77) <4444 4*82>\n" +
                    "1123 321(5) <4444 4444>\n" +
                    "(3555) (533) <4444 4*82>\n" +
                    "(77)1(5) (77)1(5) <4444 4444>\n" +
                    "(7#5#5#56#45) <4444 {44}4>\n" +
                    "11123 3211(5) <{44}444 44{48}8>\n" +
                    "(3555 533) <4444 4*82>\n" +
                    "end\n" +
                    "\n" +
                    "//双声部同时播放\n" +
                    "play(soprano&alto)";
            inputTextPane.setText(str);
            inputTextPane.setCaretPosition(0);
            hasChanged = false;
            isLoadedMidiFile = false;

            this.setTitle("Euterpe - Demo");
            tipsMenuItemActionPerformed(null);
        }
    }

    //显示提示
    private void tipsMenuItemActionPerformed(ActionEvent e) {
        isNoteMappingPageOpen = false;
        setOutputTextPaneEditable(false);

        String str = "============================================\n" +
                "                                                  Tips\n" +
                "-------------------------------------------------------------------------\n" +
                "* 你可以在“Help-Tips”中随时打开Tips\n" +
                "\n" +
                "1. 构成乐谱的成分：\n" +
                "\t1）paragraph Name  段落声明，以下各属性独立\n" +
                "\t2）instrument= 0      \t演奏的乐器（非必要 默认钢琴）\n" +
                "\t3）volume= 127        该段落的音量（非必要 默认127）\n" +
                "\t4）speed= 90\t该段落演奏速度（非必要 默认90）\n" +
                "\t5）1= C\t\t该段落调性（非必要 默认C调）\n" +
                "\t6）((1))(2)|34|[55]\t音符的音名，即音高\n" +
                "\t7）<1248{gw*}>\t音符的时值，即持续时间\n" +
                "\t8）end\t\t段落声明结束\n" +
                "\n" +
                "2. 乐谱成分的解释：\n" +
                "\t1）声部声明：标识符须以字母开头，后跟字母或数字\n" +
                "\t2）乐器音色：见“Help-Instrument”中具体说明\n" +
                "\t3）声部音量：最小值0（禁音）最大值127（最大音量）\n" +
                "\t4）声部速度：每分钟四分音符个数，即BPM\n" +
                "\t5）声部调性：CDEFGAB加上b（降号）与#（升号）\n" +
                "\t6）“( )”内为低八度，可叠加“[ ]”内为高八度，同上\n" +
                "\t7）“< >”内为全、2、4、6、8、16、32分音符与附点\n" +
                "\t8）“| |”内为同时音，该符号不可叠加，意为同时演奏的音\n" +
                "\t9）“{ }”内为连音，若音高相同则会合并成一个音\n" +
                "\t10）声明结束：须用end结束声明，对应paragraph\n" +
                "\n" +
                "3. 播放乐谱的方法：\n" +
                "\t1）通过“play( )”进行播放，( )”内为声部的标识符\n" +
                "\t2）“&”左右的声部将同时播放，\n" +
                "\t3）“ , ”左右的声部将先后播放\n" +
                "===========================================";
        outputTextPane.setText(str);
        outputTextPane.setCaretPosition(0);
    }

    //显示乐器列表
    private void instruMenuItemActionPerformed(ActionEvent e) {
        isNoteMappingPageOpen = false;
        setOutputTextPaneEditable(false);

        String str = "===========================================\n" +
                "                                            Instrument\n" +
                "-----------------------------------------------------------------------\n" +
                "音色号\t乐器名\t                |\t音色号\t乐器名\n" +
                "-----------------------------------------------------------------------\n" +
                "钢琴类\t\t                |\t簧乐器\n" +
                "0（推荐）\t大钢琴\t                |\t64\t高音萨克斯\n" +
                "1\t亮音钢琴\t                |\t65\t中音萨克斯\n" +
                "2\t电子大钢琴\t                |\t66\t次中音萨克斯\n" +
                "3\t酒吧钢琴\t                |\t67\t上低音萨克斯\n" +
                "4\t电钢琴1\t                |\t68\t双簧管\n" +
                "5\t电钢琴2\t                |\t69\t英国管\n" +
                "6\t大键琴\t                |\t70\t巴颂管\n" +
                "7\t电翼琴\t                |\t71\t单簧管\n" +
                "-----------------------------------------------------------------------\n" +
                "固定音高敲击乐器\t                |\t吹管乐器\n" +
                "8\t钢片琴\t                |\t72\t短笛\n" +
                "9\t钟琴\t                |\t73\t长笛\n" +
                "10（推荐）音乐盒\t                |\t74\t竖笛\n" +
                "11\t颤音琴\t                |\t75（推荐）牧笛\n" +
                "12\t马林巴琴\t                |\t76\t瓶笛\n" +
                "13\t木琴\t                |\t77\t尺八\n" +
                "14\t管钟\t                |\t78\t哨子\n" +
                "15\t洋琴\t                |\t79\t陶笛\n" +
                "-----------------------------------------------------------------------\n" +
                "风琴\t\t                |\t合成音主旋律\n" +
                "16\t音栓风琴\t                |\t80\t方波\n" +
                "17\t敲击风琴\t                |\t81\t锯齿波\n" +
                "18\t摇滚风琴\t                |\t82\t汽笛风琴\n" +
                "19\t教堂管风琴\t                |\t83\t合成吹管\n" +
                "20\t簧风琴\t                |\t84\t合成电吉他\n" +
                "21（推荐）手风琴\t                |\t85\t人声键\n" +
                "22\t口琴\t                |\t86\t五度音\n" +
                "23（推荐）探戈手风琴\t                |\t87\t贝斯吉他合奏\n" +
                "-----------------------------------------------------------------------\n" +
                "吉他\t\t                |\t合成音和弦衬底\n" +
                "24（推荐）木吉他（尼龙弦）      |\t88\t新时代\n" +
                "25\t木吉他（钢弦）          |\t89\t温暖的\n" +
                "26\t电吉他（爵士）          |\t90\t多重和音\n" +
                "27\t电吉他（清音）          |\t91\t唱诗班\n" +
                "28\t电吉他（闷音）          |\t92\t弓弦音色\n" +
                "29\t电吉他（驱动音效）   |\t93\t金属的\n" +
                "30\t电吉他（失真音效）   |\t94\t光华\n" +
                "31\t吉他泛音\t                |\t95\t宽阔的\n" +
                "-----------------------------------------------------------------------\n" +
                "贝斯\t\t                |\t合成音效果\n" +
                "32（推荐）贝斯\t                |\t96\t雨声\n" +
                "33\t电贝斯（指弹）          |\t97\t电影音效\n" +
                "34\t电贝斯（拨片）          |\t98\t水晶\n" +
                "35\t无品贝斯\t                |\t99\t气氛\n" +
                "36\t打弦贝斯1\t                |\t100\t明亮\n" +
                "37\t打弦贝斯2\t                |\t101\t魅影\n" +
                "38\t合成贝斯1\t                |\t102\t回音\n" +
                "39\t合成贝斯2\t                |\t103\t科幻\n" +
                "-----------------------------------------------------------------------\n" +
                "弦乐器\t\t                |\t民族乐器\n" +
                "40\t小提琴\t                |\t104\t西塔琴\n" +
                "41\t中提琴\t                |\t105\t斑鸠琴\n" +
                "42\t大提琴\t                |\t106\t三味线\n" +
                "43\t低音提琴\t                |\t107\t古筝\n" +
                "44\t颤弓弦乐\t                |\t108\t卡林巴铁片琴\n" +
                "45\t弹拨弦乐\t                |\t109\t苏格兰风琴\n" +
                "46\t竖琴\t                |\t110\t古提亲\n" +
                "47\t定音鼓\t                |\t111\t兽笛\n" +
                "-----------------------------------------------------------------------\n" +
                "合奏\t\t                |\t打击乐器\n" +
                "48\t弦乐合奏1\t                |\t112\t叮当铃\n" +
                "49\t弦乐合奏2\t                |\t113\t阿果果鼓\n" +
                "50\t合成弦乐1\t                |\t114\t钢鼓\n" +
                "51\t合成弦乐2\t                |\t115\t木鱼\n" +
                "52\t唱诗班“啊”             |\t116\t太鼓\n" +
                "53\t合唱“喔”\t                |\t117\t定音筒鼓\n" +
                "54\t合成人声\t                |\t118\t合成鼓\n" +
                "55\t交响打击乐\t                |\t119\t反钹\n" +
                "-----------------------------------------------------------------------\n" +
                "铜管乐器\t\t                |\t特殊音效\n" +
                "56\t小号\t                |\t120\t吉他滑弦杂音\n" +
                "57\t长号\t                |\t121\t呼吸杂音\n" +
                "58\t大号\t                |\t122\t海浪\n" +
                "59\t闷音小号\t                |\t123\t鸟鸣\n" +
                "60\t法国圆号\t                |\t124\t电话铃声\n" +
                "61\t铜管乐\t                |\t125\t直升机\n" +
                "62\t合成铜管1\t                |\t126\t鼓掌\n" +
                "63\t合成铜管2\t                |\t127\t枪声\n" +
                "===========================================";
        outputTextPane.setText(str);
        outputTextPane.setCaretPosition(0);
    }

    //退出
    private void exitMenuItemActionPerformed(ActionEvent e) {
        //删除临时Midi文件
        if (showSaveComfirm("Exist unsaved content, save before exit?")) {
            if (tempMidiFile != null && tempMidiFile.exists())
                tempMidiFile.delete();

            System.exit(0);
        }
    }

    private void initComponents() {
        // JFormDesigner - Component initialization - DO NOT MODIFY  //GEN-BEGIN:initComponents
        menuBar1 = new JMenuBar();
        fileMenu = new JMenu();
        newEmptyMenuItem = new JMenuItem();
        newMenuItem = new JMenuItem();
        separator2 = new JSeparator();
        openMenuItem = new JMenuItem();
        separator3 = new JSeparator();
        saveMenuItem = new JMenuItem();
        saveAsMenuItem = new JMenuItem();
        separator4 = new JSeparator();
        exitMenuItem = new JMenuItem();
        runMenu = new JMenu();
        exportMidiMenuItem = new JMenuItem();
        playMenuItem = new JMenuItem();
        playerMenu = new JMenu();
        loadSoundFontMenuItem = new JMenuItem();
        playDirectMenuItem = new JMenuItem();
        stopDirectMenuItem = new JMenuItem();
        forwardMenuItem = new JMenuItem();
        backwardMenuItem = new JMenuItem();
        fastForwardMenuItem = new JMenuItem();
        fastBackwardMenuItem = new JMenuItem();
        toolMenu = new JMenu();
        setNoteMappingMenuItem = new JMenuItem();
        helpMenu = new JMenu();
        instruMenuItem = new JMenuItem();
        tipsMenuItem = new JMenuItem();
        demoMenuItem = new JMenuItem();
        aboutMenuItem = new JMenuItem();
        panel1 = new JPanel();
        scrollPane3 = new JScrollPane();
        lineTextArea = new JTextArea();
        scrollPane1 = new JScrollPane();
        inputTextPane = new JTextPane();
        scrollPane2 = new JScrollPane();
        outputTextPane = new JTextPane();

        //======== this ========
        setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 12));
        setTitle("Euterpe - New File");
        setMinimumSize(new Dimension(916, 709));
        Container contentPane = getContentPane();
        contentPane.setLayout(new GridLayout());

        //======== menuBar1 ========
        {

            //======== fileMenu ========
            {
                fileMenu.setText("File");

                //---- newEmptyMenuItem ----
                newEmptyMenuItem.setText("New - Empty");
                newEmptyMenuItem.addActionListener(e -> newEmptyMenuItemActionPerformed(e));
                fileMenu.add(newEmptyMenuItem);

                //---- newMenuItem ----
                newMenuItem.setText("New - Template");
                newMenuItem.addActionListener(e -> newMenuItemActionPerformed(e));
                fileMenu.add(newMenuItem);
                fileMenu.add(separator2);

                //---- openMenuItem ----
                openMenuItem.setText("Open");
                openMenuItem.addActionListener(e -> openMenuItemActionPerformed(e));
                fileMenu.add(openMenuItem);
                fileMenu.add(separator3);

                //---- saveMenuItem ----
                saveMenuItem.setText("Save");
                saveMenuItem.addActionListener(e -> saveMenuItemActionPerformed(e));
                fileMenu.add(saveMenuItem);

                //---- saveAsMenuItem ----
                saveAsMenuItem.setText("Save As...");
                saveAsMenuItem.addActionListener(e -> saveAsMenuItemActionPerformed(e));
                fileMenu.add(saveAsMenuItem);
                fileMenu.add(separator4);

                //---- exitMenuItem ----
                exitMenuItem.setText("Exit");
                exitMenuItem.addActionListener(e -> exitMenuItemActionPerformed(e));
                fileMenu.add(exitMenuItem);
            }
            menuBar1.add(fileMenu);

            //======== runMenu ========
            {
                runMenu.setText("Run");

                //---- exportMidiMenuItem ----
                exportMidiMenuItem.setText("Export Midi File");
                exportMidiMenuItem.addActionListener(e -> generateMidiMenuItemActionPerformed(e));
                runMenu.add(exportMidiMenuItem);

                //---- playMenuItem ----
                playMenuItem.setText("Play Midi File");
                playMenuItem.addActionListener(e -> playMenuItemActionPerformed(e));
                runMenu.add(playMenuItem);
            }
            menuBar1.add(runMenu);

            //======== playerMenu ========
            {
                playerMenu.setText("Midi Player");

                //---- loadSoundFontMenuItem ----
                loadSoundFontMenuItem.setText("Load SoundFont");
                loadSoundFontMenuItem.addActionListener(e -> loadSoundFontMenuItemActionPerformed(e));
                playerMenu.add(loadSoundFontMenuItem);
                playerMenu.addSeparator();

                //---- playDirectMenuItem ----
                playDirectMenuItem.setText("Play");
                playDirectMenuItem.addActionListener(e -> playDirectMenuItemActionPerformed(e));
                playerMenu.add(playDirectMenuItem);

                //---- stopDirectMenuItem ----
                stopDirectMenuItem.setText("Stop");
                stopDirectMenuItem.addActionListener(e -> stopDirectMenuItemActionPerformed(e));
                playerMenu.add(stopDirectMenuItem);
                playerMenu.addSeparator();

                //---- forwardMenuItem ----
                forwardMenuItem.setText("Forward");
                forwardMenuItem.addActionListener(e -> forwardMenuItemActionPerformed(e));
                playerMenu.add(forwardMenuItem);

                //---- backwardMenuItem ----
                backwardMenuItem.setText("Backward");
                backwardMenuItem.addActionListener(e -> backwardMenuItemActionPerformed(e));
                playerMenu.add(backwardMenuItem);
                playerMenu.addSeparator();

                //---- fastForwardMenuItem ----
                fastForwardMenuItem.setText("Fast Forward");
                fastForwardMenuItem.addActionListener(e -> fastForwardMenuItemActionPerformed(e));
                playerMenu.add(fastForwardMenuItem);

                //---- fastBackwardMenuItem ----
                fastBackwardMenuItem.setText("Fast Backward");
                fastBackwardMenuItem.addActionListener(e -> fastBackwardMenuItemActionPerformed(e));
                playerMenu.add(fastBackwardMenuItem);
            }
            menuBar1.add(playerMenu);

            //======== toolMenu ========
            {
                toolMenu.setText("Tool");

                //---- setNoteMappingMenuItem ----
                setNoteMappingMenuItem.setText("Set Note Mapping");
                setNoteMappingMenuItem.addActionListener(e -> setNoteMappingMenuItemActionPerformed(e));
                toolMenu.add(setNoteMappingMenuItem);
            }
            menuBar1.add(toolMenu);

            //======== helpMenu ========
            {
                helpMenu.setText("Help");

                //---- instruMenuItem ----
                instruMenuItem.setText("Instruments");
                instruMenuItem.addActionListener(e -> instruMenuItemActionPerformed(e));
                helpMenu.add(instruMenuItem);

                //---- tipsMenuItem ----
                tipsMenuItem.setText("Tips");
                tipsMenuItem.addActionListener(e -> tipsMenuItemActionPerformed(e));
                helpMenu.add(tipsMenuItem);

                //---- demoMenuItem ----
                demoMenuItem.setText("Demo");
                demoMenuItem.addActionListener(e -> demoMenuItemActionPerformed(e));
                helpMenu.add(demoMenuItem);

                //---- aboutMenuItem ----
                aboutMenuItem.setText("About");
                aboutMenuItem.addActionListener(e -> aboutMenuItemActionPerformed(e));
                helpMenu.add(aboutMenuItem);
            }
            menuBar1.add(helpMenu);
        }
        setJMenuBar(menuBar1);

        //======== panel1 ========
        {
            panel1.setLayout(new MigLayout(
                    "insets 0,hidemode 3",
                    // columns
                    "[fill]0" +
                            "[400:400:875,grow,fill]0" +
                            "[460:460:1005,grow,fill]",
                    // rows
                    "[fill]"));

            //======== scrollPane3 ========
            {

                //---- lineTextArea ----
                lineTextArea.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
                lineTextArea.setEnabled(false);
                lineTextArea.setEditable(false);
                lineTextArea.setBorder(null);
                lineTextArea.setBackground(Color.white);
                lineTextArea.setForeground(new Color(153, 153, 153));
                scrollPane3.setViewportView(lineTextArea);
            }
            panel1.add(scrollPane3, "cell 0 0,width 40:40:40");

            //======== scrollPane1 ========
            {

                //---- inputTextPane ----
                inputTextPane.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
                inputTextPane.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
                inputTextPane.setBorder(null);
                inputTextPane.setDragEnabled(true);
                scrollPane1.setViewportView(inputTextPane);
            }
            panel1.add(scrollPane1, "cell 1 0,width 400:400:875,height 640:640:1080");

            //======== scrollPane2 ========
            {

                //---- outputTextPane ----
                outputTextPane.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
                outputTextPane.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
                outputTextPane.setBorder(null);
                outputTextPane.setSelectionColor(Color.white);
                outputTextPane.setSelectedTextColor(new Color(60, 60, 60));
                outputTextPane.setEditable(false);
                scrollPane2.setViewportView(outputTextPane);
            }
            panel1.add(scrollPane2, "cell 2 0,width 460:460:1005,height 640:640:1080");
        }
        contentPane.add(panel1);
        pack();
        setLocationRelativeTo(getOwner());
        // JFormDesigner - End of component initialization  //GEN-END:initComponents
    }

    // JFormDesigner - Variables declaration - DO NOT MODIFY  //GEN-BEGIN:variables
    private JMenuBar menuBar1;
    private JMenu fileMenu;
    private JMenuItem newEmptyMenuItem;
    private JMenuItem newMenuItem;
    private JSeparator separator2;
    private JMenuItem openMenuItem;
    private JSeparator separator3;
    private JMenuItem saveMenuItem;
    private JMenuItem saveAsMenuItem;
    private JSeparator separator4;
    private JMenuItem exitMenuItem;
    private JMenu runMenu;
    private JMenuItem exportMidiMenuItem;
    private JMenuItem playMenuItem;
    private JMenu playerMenu;
    private JMenuItem loadSoundFontMenuItem;
    private JMenuItem playDirectMenuItem;
    private JMenuItem stopDirectMenuItem;
    private JMenuItem forwardMenuItem;
    private JMenuItem backwardMenuItem;
    private JMenuItem fastForwardMenuItem;
    private JMenuItem fastBackwardMenuItem;
    private JMenu toolMenu;
    private JMenuItem setNoteMappingMenuItem;
    private JMenu helpMenu;
    private JMenuItem instruMenuItem;
    private JMenuItem tipsMenuItem;
    private JMenuItem demoMenuItem;
    private JMenuItem aboutMenuItem;
    private JPanel panel1;
    private JScrollPane scrollPane3;
    private JTextArea lineTextArea;
    private JScrollPane scrollPane1;
    private JTextPane inputTextPane;
    private JScrollPane scrollPane2;
    private JTextPane outputTextPane;
    // JFormDesigner - End of variables declaration  //GEN-END:variables

}