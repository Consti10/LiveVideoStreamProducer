package constantin.testlivevideostreamproducer;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;

//Send dummy data vid udp to other end
//The dummy data is:
//first 32 bits == seqence number
//then random data
// TODO unimplemented
public class ADemoUDPStream extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ademo_udpstream);
    }
}
