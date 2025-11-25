package ch.unisg.mmpmod.ambiguityresolver.ml;

import java.util.List;

public interface MLService {
    String analyzeFrames(List<String> frame_paths) throws Exception;
}
