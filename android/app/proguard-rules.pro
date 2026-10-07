# Manifest components and the scanner's XML-instantiated views must retain their names.
-keep public class com.journeyapps.barcodescanner.BarcodeView { public <init>(...); }
-keep public class com.journeyapps.barcodescanner.ViewfinderView { public <init>(...); }
-keep public class com.journeyapps.barcodescanner.DecoratedBarcodeView { public <init>(...); }
