package me.cookie.kwrg.duck;

import me.cookie.kwrg.render.utils.LoadingAudio;

public interface KwrgAudioLibrary {
    LoadingAudio.PreparedDevice kwrg$prepare(LoadingAudio.DeviceRequest request);

    void kwrg$setPrepared(LoadingAudio.PreparedDevice prepared);
}
