package component;

import entity.interpreter.Node;
import entity.interpreter.Note;
import entity.interpreter.Paragraph;
import entity.midi.MidiFile;
import entity.midi.MidiTrack;
import javafx.util.Pair;

import java.util.*;

public class Semantic {

    private Node abstractSyntaxTree;

    private List<Integer> errorLines;

    private StringBuilder errorInfo;

    private int toneOffset;

    private int haftToneOffset;

    private Map<String, Paragraph> paragraphMap;

    private MidiFile midiFile;

    private List<MidiTrack> midiTracks;

    public String interpret(Node abstractSyntaxTree) {
        this.abstractSyntaxTree = abstractSyntaxTree;

        errorLines = new ArrayList<>();
        errorInfo = new StringBuilder();

        toneOffset = 0;
        haftToneOffset = 0;

        paragraphMap = new HashMap<>();

        midiTracks = new ArrayList<>();

        processTreeNode(this.abstractSyntaxTree, null);

        if (getIsError())
            return null;

        midiFile = new MidiFile(midiTracks);
        midiFile.construct();

        return midiFile.toString();
    }

    private void processTreeNode(Node curNode, Paragraph para) {
        Paragraph paragraph = para;
        List<Integer> noteList;
        List<Integer> durationList;
        int lineNoteCount = 0;
        int lineRhythmCount = 0;

        for (Node child : curNode.getChildren()) {
            switch (child.getType()) {
                case "score":
                    processTreeNode(child, paragraph);
                    break;

                case "execution":
                    processTreeNode(child, paragraph);
                    break;

                case "statement":
                    if (paragraphMap.containsKey(child.getChild(0).getContent())) {
                        errorInfo.append("Line: ").append(child.getChild(0).getLine()).append("\t重复声明的段落名").append(child.getChild(0).getContent()).append("\n");
                        errorLines.add(child.getChild(0).getLine());
                    }
                    paragraph = new Paragraph();
                    paragraphMap.put(child.getChild(0).getContent(), paragraph);
                    break;

                case "instrument":
                    if (child.getChild(0).getContent().length() < 4 && Integer.parseInt(child.getChild(0).getContent()) >= 0 && Integer.parseInt(child.getChild(0).getContent()) < 128) {
                        paragraph.setInstrument(Byte.parseByte(child.getChild(0).getContent()));
                    } else {
                        errorInfo.append("Line: ").append(child.getChild(0).getLine()).append("\t乐器声明超出范围（0-127）\n");
                        errorLines.add(child.getChild(0).getLine());
                    }
                    break;

                case "volume":
                    if (child.getChild(0).getContent().length() < 4 && Integer.parseInt(child.getChild(0).getContent()) >= 0 && Integer.parseInt(child.getChild(0).getContent()) < 128) {
                        paragraph.setVolume(Byte.parseByte(child.getChild(0).getContent()));
                    } else {
                        errorInfo.append("Line: ").append(child.getChild(0).getLine()).append("\t音量声明超出范围（0-127）\n");
                        errorLines.add(child.getChild(0).getLine());
                    }
                    break;

                case "speed":
                    if (child.getChild(0).getContent().length() < 4) {
                        paragraph.setSpeed(Float.parseFloat(child.getChild(0).getContent()));
                    } else {
                        errorInfo.append("Line: ").append(child.getChild(0).getLine()).append("\t速度声明超出范围（0-999）\n");
                        errorLines.add(child.getChild(0).getLine());
                    }
                    break;

                case "tonality":
                    toneOffset = 0;
                    for (Node tonality : child.getChildren()) {
                        switch (tonality.getContent()) {
                            case "#":
                                toneOffset += 1;
                                break;
                            case "b":
                                toneOffset -= 1;
                                break;
                            case "C":
                                break;
                            case "D":
                                toneOffset += 2;
                                break;
                            case "E":
                                toneOffset += 4;
                                break;
                            case "F":
                                toneOffset += 5;
                                break;
                            case "G":
                                toneOffset += 7;
                                break;
                            case "A":
                                toneOffset -= 3;
                                break;
                            case "B":
                                toneOffset -= 1;
                                break;
                        }
                    }
                    break;

                case "sentence":
                    processTreeNode(child, paragraph);
                    break;

                case "end paragraph":
                    break;

                case "melody":
                    noteList = paragraph.getNoteList();
                    for (Node tone : child.getChildren()) {
                        switch (tone.getContent()) {
                            case "(":
                                toneOffset -= 12;
                                break;
                            case ")":
                                toneOffset += 12;
                                break;
                            case "[":
                                toneOffset += 12;
                                break;
                            case "]":
                                toneOffset -= 12;
                                break;
                            case "|":
                                paragraph.getSymbolQueue().offer(new Pair<>(0, noteList.size()));
                                break;
                            case "#":
                                haftToneOffset = 1;
                                break;
                            case "b":
                                haftToneOffset = -1;
                                break;
                            case "0":
                                lineNoteCount++;
                                noteList.add(0);
                                break;
                            case "1":
                                lineNoteCount++;
                                noteList.add(60 + haftToneOffset + toneOffset);
                                haftToneOffset = 0;
                                break;
                            case "2":
                                lineNoteCount++;
                                noteList.add(62 + haftToneOffset + toneOffset);
                                haftToneOffset = 0;
                                break;
                            case "3":
                                lineNoteCount++;
                                noteList.add(64 + haftToneOffset + toneOffset);
                                haftToneOffset = 0;
                                break;
                            case "4":
                                lineNoteCount++;
                                noteList.add(65 + haftToneOffset + toneOffset);
                                haftToneOffset = 0;
                                break;
                            case "5":
                                lineNoteCount++;
                                noteList.add(67 + haftToneOffset + toneOffset);
                                haftToneOffset = 0;
                                break;
                            case "6":
                                lineNoteCount++;
                                noteList.add(69 + haftToneOffset + toneOffset);
                                haftToneOffset = 0;
                                break;
                            case "7":
                                lineNoteCount++;
                                noteList.add(71 + haftToneOffset + toneOffset);
                                haftToneOffset = 0;
                                break;
                        }
                    }
                    break;

                case "rhythm":
                    durationList = paragraph.getDurationList();
                    Integer line = child.getChild(0).getLine();
                    for (Node rhythm : child.getChildren()) {
                        switch (rhythm.getContent()) {
                            case "{":
                                paragraph.getSymbolQueue().offer(new Pair<>(1, durationList.size()));
                                break;
                            case "}":
                                paragraph.getSymbolQueue().offer(new Pair<>(2, durationList.size()));
                                break;
                            case "1":
                                lineRhythmCount++;
                                durationList.add(480);
                                break;
                            case "1*":
                                lineRhythmCount++;
                                durationList.add(720);
                                break;
                            case "2":
                                lineRhythmCount++;
                                durationList.add(240);
                                break;
                            case "2*":
                                lineRhythmCount++;
                                durationList.add(360);
                                break;
                            case "4":
                                lineRhythmCount++;
                                durationList.add(120);
                                break;
                            case "4*":
                                lineRhythmCount++;
                                durationList.add(180);
                                break;
                            case "8":
                                lineRhythmCount++;
                                durationList.add(60);
                                break;
                            case "8*":
                                lineRhythmCount++;
                                durationList.add(90);
                                break;
                            case "g":
                                lineRhythmCount++;
                                durationList.add(30);
                                break;
                            case "g*":
                                lineRhythmCount++;
                                durationList.add(45);
                                break;
                            case "w":
                                lineRhythmCount++;
                                durationList.add(15);
                                break;
                            case "w*":
                                lineRhythmCount++;
                                errorInfo.append("Line: ").append(line).append("\t不支持32分附点音符，即w*\n");
                                errorLines.add(line);
                                break;
                        }
                    }

                    if (lineNoteCount != lineRhythmCount) {
                        errorInfo.append("Line: ").append(line).append("\t该句音符与时值数量不相同\n");
                        errorLines.add(line);
                    }

                    break;

                case "playlist":
                    String paraName = "";
                    int index = 0;
                    int totalDuration = 0;

                    for (Node playList : child.getChildren()) {
                        switch (playList.getContent()) {
                            case "&":
                                index++;
                                break;

                            case ",":
                                if (!paragraphMap.containsKey(paraName))
                                    break;

                                index = 0;

                                List<Integer> duration = paragraphMap.get(paraName).getDurationList();

                                for (int dura : duration)
                                    totalDuration += dura;

                                break;

                            default:
                                paraName = playList.getContent();

                                if (!paragraphMap.containsKey(paraName)) {
                                    errorInfo.append("Line: ").append(playList.getLine()).append("\t未声明的段落名").append(paraName).append("\n");
                                    errorLines.add(playList.getLine());
                                    break;
                                }

                                MidiTrack midiTrack;

                                if (index > midiTracks.size() - 1) {
                                    midiTrack = constuctMidiTrackPart(paragraphMap.get(paraName), totalDuration, (byte) index);
                                    midiTracks.add(midiTrack);
                                } else {
                                    midiTrack = constuctMidiTrackPart(paragraphMap.get(paraName), 0, (byte) index);
                                    midiTracks.get(index).merge(midiTrack);
                                }

                                break;
                        }
                    }

                    if (!getIsError())
                        for (MidiTrack midiTrack : midiTracks)
                            midiTrack.setEnd();
            }
        }
    }

    private MidiTrack constuctMidiTrackPart(Paragraph paragraph, int duration, byte channel) {
        if (getIsError())
            return null;

        MidiTrack midiTrack = new MidiTrack();
        midiTrack.setBpm(paragraph.getSpeed());
        midiTrack.setInstrument(channel, paragraph.getInstrument());
        midiTrack.addController(channel, (byte) 0x07, paragraph.getVolume());

        if (duration != 0)
            midiTrack.setDuration(duration);

        List<Integer> noteList = paragraph.getNoteList();
        List<Integer> durationList = paragraph.getDurationList();

        Queue<Note> bufferNotes = new PriorityQueue<>(Comparator.comparingInt(Note::getDeltaTime));

        Queue<Pair<Integer, Integer>> symbolQueue = paragraph.getSymbolQueue();

        Note tempNote;

        int count = noteList.size();
        for (int i = 0; i < count; i++) {
            if (!symbolQueue.isEmpty() && symbolQueue.peek().getValue() == i) {
                //i为符号后一个音符
                switch (symbolQueue.poll().getKey()) {
                    case 0:
                        if (!symbolQueue.isEmpty()) {
                            if (symbolQueue.peek().getKey() != 0) {
                                errorInfo.append("Line: 未知\t同时音间存在连音无意义\n");
                                errorLines.add(0);
                                break;
                            }

                            while (!bufferNotes.isEmpty()) {
                                tempNote = bufferNotes.poll();
                                midiTrack.insertNoteOff(tempNote.getDeltaTime(), channel, tempNote.getNote());
                                reduceDeltaTimeInQueue(bufferNotes, tempNote.getDeltaTime());
                            }

                            for (boolean isPrimary = true; true; isPrimary = false) {
                                midiTrack.insertNoteOn(channel, noteList.get(i).byteValue(), (byte) 120);
                                tempNote = new Note(durationList.get(i), noteList.get(i++).byteValue(), isPrimary);
                                bufferNotes.offer(tempNote);

                                if (symbolQueue.peek().getValue() == i)
                                    break;
                            }

                            symbolQueue.poll();
                            do {
                                tempNote = bufferNotes.poll();
                                midiTrack.insertNoteOff(tempNote.getDeltaTime(), channel, tempNote.getNote());
                                reduceDeltaTimeInQueue(bufferNotes, tempNote.getDeltaTime());
                            } while (tempNote.getIsPrimary() != true);
                        }
                        break;

                    case 1:
                        if (!symbolQueue.isEmpty()) {
                            if (symbolQueue.peek().getKey() != 2) {
                                errorInfo.append("Line: 未知\t连音间存在同时音无意义\n");
                                errorLines.add(0);
                                break;
                            }

                            do {
                                //处理连音左括号
                                i++;
                            } while (symbolQueue.peek().getValue() != i);

                            //读到连音右括号
                            symbolQueue.poll();

                        }
                        break;
                }
            }

            if (i < count) {
                midiTrack.insertNoteOn(channel, noteList.get(i).byteValue(), (byte) 120);

                if (!bufferNotes.isEmpty()) {
                    //同时音
                    while (!bufferNotes.isEmpty() && durationList.get(i) >= bufferNotes.peek().getDeltaTime()) {
                        tempNote = bufferNotes.poll();
                        midiTrack.insertNoteOff(tempNote.getDeltaTime(), channel, tempNote.getNote());
                        reduceDeltaTimeInQueue(bufferNotes, tempNote.getDeltaTime());
                        durationList.set(i, durationList.get(i) - tempNote.getDeltaTime());
                    }
                    reduceDeltaTimeInQueue(bufferNotes, durationList.get(i));
                }
                midiTrack.insertNoteOff(durationList.get(i), channel, noteList.get(i).byteValue());
            }
        }

        while (!bufferNotes.isEmpty()) {
            tempNote = bufferNotes.poll();
            midiTrack.insertNoteOff(tempNote.getDeltaTime(), channel, tempNote.getNote());
            reduceDeltaTimeInQueue(bufferNotes, tempNote.getDeltaTime());
        }

        return midiTrack;
    }

    private void reduceDeltaTimeInQueue(Queue<Note> bufferNotes, int deltaTime) {
        for (Note noteInQueue : bufferNotes) {
            noteInQueue.setDeltaTime(noteInQueue.getDeltaTime() - deltaTime);
        }
    }

    public boolean getIsError() {
        return !errorLines.isEmpty();
    }

    public List<Integer> getErrorLines() {
        return errorLines;
    }

    public String getErrors() {
        return errorInfo.toString();
    }

    public MidiFile getMidiFile() {
        return midiFile;
    }

}