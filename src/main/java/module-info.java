module com.example.demo {
    requires javafx.controls;
    requires javafx.fxml;
    requires jtransforms;


    opens com.example.spectrumviewer to javafx.fxml;
    exports com.example.spectrumviewer;
}