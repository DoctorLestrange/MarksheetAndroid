# Marksheet production R8 / ProGuard rules

# Preserve source file names and line numbers for readable production crash reports
-keepattributes SourceFile,LineNumberTable

# Keep domain enums used with valueOf() in Room and DataStore mapping
-keepclassmembers enum com.example.domain.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Room entities and relational POJOs
-keep class com.example.data.ExamSessionEntity { *; }
-keep class com.example.data.QuestionRecordEntity { *; }
-keep class com.example.data.SessionWithRecords { *; }
