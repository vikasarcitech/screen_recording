import 'package:flutter/material.dart';
import 'package:flutter_screen_recording/flutter_screen_recording.dart';
import 'package:path_provider/path_provider.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:http/http.dart' as http;
import 'dart:convert';
import 'package:uuid/uuid.dart';
import 'package:device_info_plus/device_info_plus.dart';
import 'dart:io';
import 'dart:async';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Interview Recorder',
      theme: ThemeData(
        primarySwatch: Colors.blue,
      ),
      home: const ScreenRecorderPage(),
    );
  }
}

class ScreenRecorderPage extends StatefulWidget {
  const ScreenRecorderPage({super.key});

  @override
  _ScreenRecorderPageState createState() => _ScreenRecorderPageState();
}

class _ScreenRecorderPageState extends State<ScreenRecorderPage> {
  bool _isRecording = false;
  String? _finalRecordingPath;
  int _chunkCount = 0;
  List<String> _uploadedChunks = [];
  String _interviewId = '';
  final Uuid _uuid = const Uuid();
  Timer? _chunkTimer;
  DateTime? _lastChunkTime;
  String? _currentChunkPath;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Interview Recorder'),
      ),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            ElevatedButton(
              onPressed: _isRecording ? stopRecording : startRecording,
              child: Text(_isRecording ? 'Stop Recording' : 'Start Recording'),
            ),
            if (_finalRecordingPath != null)
              Padding(
                padding: const EdgeInsets.all(16.0),
                child: Text('Recording saved to: $_finalRecordingPath'),
              ),
            Text('Chunks uploaded: $_chunkCount'),
            if (_interviewId.isNotEmpty)
              Padding(
                padding: const EdgeInsets.all(8.0),
                child: Text('Interview ID: $_interviewId'),
              ),
          ],
        ),
      ),
    );
  }

  Future<void> startRecording() async {
    // Check and request permissions
    bool permissionsGranted = await checkAndRequestPermissions();
    if (!permissionsGranted) return;

    try {
      // Generate unique interview ID
      _interviewId = _uuid.v4();

      // Start screen recording
      bool started = await FlutterScreenRecording.startRecordScreen(
        "Interview Recording - $_interviewId",
        titleNotification: "Screen Recording",
        messageNotification: "Recording in progress",
      );

      if (started) {
        setState(() {
          _isRecording = true;
          _chunkCount = 0;
          _uploadedChunks = [];
          _lastChunkTime = DateTime.now();
        });

        await _createNewChunkFile();
        _startChunkTimer();
      } else {
        throw Exception('Failed to start recording');
      }
    } catch (e) {
      debugPrint("Error starting recording: $e");
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Failed to start recording: $e')),
      );
    }
  }

  Future<bool> checkAndRequestPermissions() async {
    if (Platform.isAndroid) {
      final androidInfo = await DeviceInfoPlugin().androidInfo;
      if (androidInfo.version.sdkInt <= 28) { // Android 9 and lower
        var storageStatus = await Permission.storage.status;
        if (!storageStatus.isGranted) {
          storageStatus = await Permission.storage.request();
          if (!storageStatus.isGranted) {
            _showPermissionDeniedMessage([Permission.storage]);
            return false;
          }
        }
      }
    }

    // Always request microphone permission
    var microphoneStatus = await Permission.microphone.status;
    if (!microphoneStatus.isGranted) {
      microphoneStatus = await Permission.microphone.request();
      if (!microphoneStatus.isGranted) {
        _showPermissionDeniedMessage([Permission.microphone]);
        return false;
      }
    }

    return true;
  }

  Future<void> _createNewChunkFile() async {
    final appDir = await getAppSpecificDirectory();
    final dir = Directory(appDir);
    if (!await dir.exists()) {
      await dir.create(recursive: true);
    }
    _currentChunkPath = '$appDir/chunk_${DateTime.now().millisecondsSinceEpoch}_$_interviewId.mp4';
    await File(_currentChunkPath!).writeAsBytes([]);
  }

  Future<String> getAppSpecificDirectory() async {
    if (Platform.isAndroid) {
      final directory = await getExternalStorageDirectory();
      print('${directory?.path}/InterviewRecordings');
      return '${directory?.path}/InterviewRecordings';
    } else {
      final directory = await getApplicationDocumentsDirectory();
      return '${directory.path}/InterviewRecordings';
    }
  }

  Future<void> stopRecording() async {
    try {
      _chunkTimer?.cancel();

      String? videoPath = await FlutterScreenRecording.stopRecordScreen;

      if (videoPath != null) {
        setState(() {
          _finalRecordingPath = videoPath;
          _isRecording = false;
        });

        await _uploadChunk(videoPath);
        await _finalizeRecording();
      }
    } catch (e) {
      debugPrint("Error stopping recording: $e");
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Failed to stop recording: $e')),
      );
    }
  }

  void _startChunkTimer() {
    _chunkTimer = Timer.periodic(const Duration(seconds: 30), (timer) async {
      if (!_isRecording) {
        timer.cancel();
        return;
      }

      try {
        if (_currentChunkPath != null && File(_currentChunkPath!).existsSync()) {
          await _uploadChunk(_currentChunkPath!);
          await _createNewChunkFile();
          setState(() {
            _chunkCount++;
            _lastChunkTime = DateTime.now();
          });
        }
      } catch (e) {
        debugPrint("Error in chunk upload: $e");
      }
    });
  }

  Future<void> _uploadChunk(String filePath) async {
    try {
      final file = File(filePath);
      final fileName = filePath.split('/').last;

      final presignedUrl = await _getPresignedUrl(fileName);
      final bytes = await file.readAsBytes();

      final response = await http.put(
        Uri.parse(presignedUrl),
        body: bytes,
        headers: {
          'Content-Type': 'video/mp4',
          'Content-Length': bytes.length.toString(),
        },
      );

      if (response.statusCode == 200) {
        _uploadedChunks.add(fileName);
      } else {
        throw Exception('Upload failed with status ${response.statusCode}');
      }
    } catch (e) {
      debugPrint("Error uploading chunk: $e");
      rethrow;
    }
  }

  Future<String> _getPresignedUrl(String fileName) async {
    final response = await http.post(
      Uri.parse('http://10.0.2.2:3000/presigned-url'),
      body: json.encode({
        'fileName': fileName,
        'contentType': 'video/mp4',
        'interviewId': _interviewId,
      }),
      headers: {'Content-Type': 'application/json'},
    );

    if (response.statusCode == 200) {
      final data = json.decode(response.body);
      return data['url'];
    } else {
      throw Exception('Failed to get pre-signed URL');
    }
  }

  Future<void> _finalizeRecording() async {
    try {
      final response = await http.post(
        Uri.parse('http://10.0.2.2:3000/finalize'),
        body: json.encode({
          'chunks': _uploadedChunks,
          'interviewId': _interviewId,
          'finalPath': _finalRecordingPath,
        }),
        headers: {'Content-Type': 'application/json'},
      );

      if (response.statusCode != 200) {
        throw Exception('Failed to finalize recording');
      }
    } catch (e) {
      debugPrint("Error finalizing recording: $e");
      rethrow;
    }
  }

  void _showPermissionDeniedMessage(List<Permission> deniedPermissions) {
    String message;

    if (deniedPermissions.contains(Permission.microphone)) {
      message = 'Microphone permission is required for audio recording';
    } else {
      message = 'Required permissions were not granted';
    }

    final snackBar = SnackBar(
      content: Text(message),
      duration: const Duration(seconds: 5),
      action: SnackBarAction(
        label: 'Settings',
        onPressed: openAppSettings,
      ),
    );

    ScaffoldMessenger.of(context).showSnackBar(snackBar);
  }

  @override
  void dispose() {
    _chunkTimer?.cancel();
    if (_isRecording) {
      stopRecording();
    }
    super.dispose();
  }
}