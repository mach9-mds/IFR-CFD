' =============================================================================
' export_cfd_params.bas  --  3DEXPERIENCE CATIA macro (VBA)
'
' Writes every parameter in the "CFD EXPORT" parameter set to a CSV, one line
' per parameter:    name,value        e.g.   FL_AxisY,-0.999847695
' (names may carry a path prefix; the STAR-CCM+ reader strips it)
'
' Read-only: it only reads parameter values, it never changes the model.
' Run it with the part that holds the CFD EXPORT set open and active.
' A Save dialog asks where to write the CSV: save it in the repo's CAD folder
' with the same name as the STEP (e.g. IFR_AERO_CAR_v2.csv).
' =============================================================================
Option Explicit

Const PARAM_SET_NAME As String = "CFD EXPORT"

Sub CATMain()
    Dim ed As Object, prt As Object, paramSet As Object, params As Object, p As Object
    Dim outPath As String
    Dim fnum As Integer, i As Long

    On Error GoTo Fail

    Set ed = CATIA.ActiveEditor
    Set prt = ed.ActiveObject

    Set paramSet = FindSet(prt.Parameters.RootParameterSet, PARAM_SET_NAME)
    If paramSet Is Nothing Then
        MsgBox "Parameter set '" & PARAM_SET_NAME & "' not found in the active part."
        Exit Sub
    End If

    ' Save dialog: returns the full path, or "" if cancelled.
    ' 1 = CatFileSelectionModeSave
    outPath = CATIA.FileSelectionBox("Save CFD parameters (same name as the STEP)", "*.csv", 1)
    If outPath = "" Then Exit Sub
    If VBA.LCase$(VBA.Right$(outPath, 4)) <> ".csv" Then outPath = outPath & ".csv"

    Set params = paramSet.DirectParameters
    fnum = FreeFile
    Open outPath For Output As #fnum
    Print #fnum, "# exported " & VBA.Format$(VBA.Now, "yyyy-mm-dd hh:nn")
    For i = 1 To params.Count
        Set p = params.Item(i)
        Print #fnum, p.Name & "," & VBA.Replace(p.ValueAsString, ",", ".")
    Next i
    Close #fnum

    MsgBox "Wrote " & params.Count & " parameters to " & outPath
    Exit Sub

Fail:
    MsgBox "Export failed: " & Err.Description
    On Error Resume Next
    Close #fnum
End Sub

' Finds a parameter set by name, searching sub-sets too. Returns Nothing if absent.
Private Function FindSet(ByVal parent As Object, ByVal wanted As String) As Object
    Dim i As Long
    Dim child As Object
    Dim hit As Object
    For i = 1 To parent.ParameterSets.Count
        Set child = parent.ParameterSets.Item(i)
        If child.Name = wanted Then
            Set FindSet = child
            Exit Function
        End If
        Set hit = FindSet(child, wanted)
        If Not hit Is Nothing Then
            Set FindSet = hit
            Exit Function
        End If
    Next i
    Set FindSet = Nothing
End Function
