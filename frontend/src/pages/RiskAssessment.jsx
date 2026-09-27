import React, { useState, useEffect } from 'react';
import { useParams } from 'react-router-dom';
import { AlertTriangle, TrendingUp, Shield, Activity, AlertCircle, CheckCircle } from 'lucide-react';
import axios from 'axios';

const RiskAssessment = () => {
  const { taskId } = useParams();
  const [assessment, setAssessment] = useState(null);
  const [loading, setLoading] = useState(true);

  const API_BASE = 'http://localhost:8080/api';

  useEffect(() => {
    loadRiskAssessment();
  }, [taskId]);

  const loadRiskAssessment = async () => {
    setLoading(true);
    try {
      const response = await axios.get(`${API_BASE}/risks/${taskId}/assessment`);
      setAssessment(response.data);
    } catch (error) {
      console.error('Failed to load risk assessment:', error);
    } finally {
      setLoading(false);
    }
  };

  const getRiskLevelColor = (level) => {
    switch (level) {
      case 'CRITICAL': return 'from-red-500 to-red-600';
      case 'HIGH': return 'from-orange-500 to-orange-600';
      case 'MEDIUM': return 'from-yellow-500 to-yellow-600';
      case 'LOW': return 'from-green-500 to-green-600';
      default: return 'from-gray-500 to-gray-600';
    }
  };

  const getRiskScoreColor = (score) => {
    if (score == null || Number.isNaN(Number(score))) return 'text-gray-500';
    if (score >= 75) return 'text-red-600';
    if (score >= 50) return 'text-orange-600';
    if (score >= 25) return 'text-yellow-600';
    return 'text-green-600';
  };

  const getRiskIcon = (type) => {
    switch (type) {
      case 'FRAUD': return <AlertTriangle className="text-red-500" size={24} />;
      case 'LIQUIDITY': return <Activity className="text-blue-500" size={24} />;
      case 'OPERATIONAL': return <TrendingUp className="text-orange-500" size={24} />;
      case 'MARKET': return <Shield className="text-purple-500" size={24} />;
      default: return <AlertCircle className="text-gray-500" size={24} />;
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-screen bg-gradient-to-br from-gray-50 to-red-50">
        <div className="text-center">
          <div className="animate-spin rounded-full h-16 w-16 border-b-4 border-red-600 mx-auto mb-4"></div>
          <p className="text-gray-600 font-medium">Analyzing risks...</p>
        </div>
      </div>
    );
  }

  if (!assessment) {
    return (
      <div className="flex items-center justify-center h-screen bg-gradient-to-br from-gray-50 to-red-50">
        <p className="text-gray-600">No risk assessment data available</p>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-gray-50 to-red-50 p-6">
      <div className="max-w-7xl mx-auto">
        {/* Header */}
        <div className="mb-8">
          <h1 className="text-3xl font-bold text-gray-900 mb-2">Risk Assessment</h1>
          <p className="text-gray-600">Comprehensive risk analysis for Task {taskId}</p>
        </div>

        {/* Overall Risk Score */}
        <div className="bg-white rounded-2xl shadow-xl p-8 mb-6 border border-gray-100">
          <div className="text-center">
            <div className="inline-flex items-center justify-center w-32 h-32 rounded-full bg-gradient-to-br from-red-100 to-orange-100 mb-4">
              <div className={`text-5xl font-bold ${getRiskScoreColor(assessment.overallRiskScore)}`}>
                {assessment.overallRiskScore == null ? '—' : Number(assessment.overallRiskScore).toFixed(0)}
              </div>
            </div>
            <div className={`inline-block px-6 py-2 rounded-full text-sm font-bold bg-gradient-to-r ${getRiskLevelColor(assessment.overallRiskLevel)} text-white mb-4`}>
              {assessment.overallRiskLevel || '未评估'}
            </div>
            <p className="text-gray-700 text-lg max-w-2xl mx-auto">{assessment.summary}</p>
          </div>
        </div>

        {/* Risk Categories Grid */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6 mb-6">
          {Object.entries(assessment.riskScores || {}).map(([type, data]) => (
            <div key={type} className="bg-white rounded-2xl shadow-lg p-6 hover:shadow-xl transition-all duration-300 border border-gray-100">
              <div className="flex items-center justify-between mb-4">
                <div className="flex items-center gap-3">
                  {getRiskIcon(type)}
                  <h3 className="font-bold text-gray-900 text-lg">{type}</h3>
                </div>
                <span className={`px-4 py-1.5 rounded-full text-xs font-bold bg-gradient-to-r ${getRiskLevelColor(data.level)} text-white shadow-sm`}>
                  {data.level}
                </span>
              </div>
              <div className="mb-4">
                <div className="flex justify-between text-sm text-gray-600 mb-2">
                  <span className="font-medium">Risk Score</span>
                  <span className={`font-bold text-lg ${getRiskScoreColor(data.score)}`}>
                    {data.score == null ? '—' : Number(data.score).toFixed(0)}
                  </span>
                </div>
                <div className="w-full bg-gray-200 rounded-full h-3 overflow-hidden">
                  <div
                    className={`h-3 rounded-full bg-gradient-to-r ${getRiskLevelColor(data.level)} transition-all duration-500`}
                    style={{ width: `${data.score}%` }}
                  ></div>
                </div>
              </div>
            </div>
          ))}
        </div>

        {/* Risk Alerts */}
        {assessment.alerts && assessment.alerts.length > 0 && (
          <div className="bg-white rounded-2xl shadow-lg mb-6 overflow-hidden border border-gray-100">
            <div className="bg-gradient-to-r from-red-500 to-orange-500 px-6 py-4">
              <h2 className="text-xl font-bold text-white flex items-center gap-2">
                <AlertCircle size={24} />
                Risk Alerts
              </h2>
            </div>
            <div className="divide-y divide-gray-100">
              {assessment.alerts.map((alert, index) => (
                <div key={index} className="p-6 hover:bg-gray-50 transition-colors">
                  <div className="flex items-start gap-4">
                    <div className="flex-shrink-0 mt-1">
                      {getRiskIcon(alert.riskType)}
                    </div>
                    <div className="flex-1">
                      <div className="flex items-center justify-between mb-2">
                        <h3 className="font-bold text-gray-900 text-lg">{alert.title}</h3>
                        <span className={`px-4 py-1.5 rounded-full text-xs font-bold bg-gradient-to-r ${getRiskLevelColor(alert.riskLevel)} text-white`}>
                          {alert.riskLevel}
                        </span>
                      </div>
                      <p className="text-gray-700 mb-3 leading-relaxed">{alert.description}</p>
                      <div className="bg-orange-50 border-l-4 border-orange-400 p-4 mb-3 rounded-r-lg">
                        <p className="text-sm text-orange-900">
                          <span className="font-semibold">Impact:</span> {alert.impact}
                        </p>
                      </div>
                      <div className="bg-blue-50 border-l-4 border-blue-400 p-4 rounded-r-lg">
                        <p className="text-sm text-blue-900">
                          <span className="font-semibold">Recommendation:</span> {alert.recommendation}
                        </p>
                      </div>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Assessment Details */}
        <div className="bg-white rounded-2xl shadow-lg p-6 border border-gray-100">
          <h2 className="text-xl font-bold text-gray-900 mb-4 flex items-center gap-2">
            <CheckCircle className="text-green-500" size={24} />
            Assessment Details
          </h2>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div className="bg-gradient-to-br from-blue-50 to-blue-100 p-4 rounded-xl">
              <div className="text-sm text-blue-600 font-medium mb-1">Generated At</div>
              <div className="text-lg font-bold text-blue-900">
                {new Date(assessment.generatedAt).toLocaleString()}
              </div>
            </div>
            <div className="bg-gradient-to-br from-orange-50 to-orange-100 p-4 rounded-xl">
              <div className="text-sm text-orange-600 font-medium mb-1">Total Alerts</div>
              <div className="text-lg font-bold text-orange-900">
                {assessment.alerts?.length || 0}
              </div>
            </div>
            <div className="bg-gradient-to-br from-purple-50 to-purple-100 p-4 rounded-xl">
              <div className="text-sm text-purple-600 font-medium mb-1">Risk Categories</div>
              <div className="text-lg font-bold text-purple-900">
                {Object.keys(assessment.riskScores || {}).length}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default RiskAssessment;
