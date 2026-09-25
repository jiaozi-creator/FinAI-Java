import React, { useState, useEffect } from 'react';
import { useParams } from 'react-router-dom';
import { TrendingUp, TrendingDown, Minus, Award, Target } from 'lucide-react';
import axios from 'axios';

const IndustryComparison = () => {
  const { taskId } = useParams();
  const [comparison, setComparison] = useState(null);
  const [loading, setLoading] = useState(true);

  const API_BASE = 'http://localhost:8080/api';

  useEffect(() => {
    loadComparison();
  }, [taskId]);

  const loadComparison = async () => {
    setLoading(true);
    try {
      const response = await axios.get(`${API_BASE}/comparison/${taskId}/suggested-peers`);
      const peers = response.data || [];
      if (peers.length > 0) {
        const compResponse = await axios.post(`${API_BASE}/comparison/${taskId}/peers`, peers);
        setComparison(compResponse.data);
      }
    } catch (error) {
      console.error('Failed to load comparison:', error);
    } finally {
      setLoading(false);
    }
  };

  const getTrendIcon = (value) => {
    if (value > 10) return <TrendingUp className="text-green-500" size={20} />;
    if (value < -10) return <TrendingDown className="text-red-500" size={20} />;
    return <Minus className="text-gray-400" size={20} />;
  };

  const getDifferenceColor = (value) => {
    if (value > 10) return 'text-green-600 bg-green-50';
    if (value < -10) return 'text-red-600 bg-red-50';
    return 'text-gray-600 bg-gray-50';
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-screen bg-gradient-to-br from-gray-50 to-purple-50">
        <div className="text-center">
          <div className="animate-spin rounded-full h-16 w-16 border-b-4 border-purple-600 mx-auto mb-4"></div>
          <p className="text-gray-600 font-medium">Loading comparison...</p>
        </div>
      </div>
    );
  }

  if (!comparison) {
    return (
      <div className="flex items-center justify-center h-screen bg-gradient-to-br from-gray-50 to-purple-50">
        <p className="text-gray-600">No comparison data available</p>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-gray-50 to-purple-50 p-6">
      <div className="max-w-7xl mx-auto">
        {/* Header */}
        <div className="mb-8">
          <h1 className="text-3xl font-bold text-gray-900 mb-2 flex items-center gap-3">
            <div className="w-10 h-10 bg-gradient-to-br from-purple-500 to-pink-600 rounded-xl flex items-center justify-center">
              <Target className="text-white" size={24} />
            </div>
            Industry Comparison
          </h1>
          <p className="text-gray-600 ml-13">
            {comparison.targetCompany?.companyName} ({comparison.targetCompany?.companyCode}) vs Industry Peers
          </p>
        </div>

        {/* Summary Card */}
        <div className="bg-white rounded-2xl shadow-xl p-8 mb-6 border border-gray-100">
          <div className="mb-6">
            <h2 className="text-2xl font-bold text-gray-900 mb-3">Analysis Summary</h2>
            <p className="text-gray-700 text-lg leading-relaxed">{comparison.summary}</p>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            {/* Strengths */}
            {comparison.strengths && comparison.strengths.length > 0 && (
              <div className="bg-gradient-to-br from-green-50 to-emerald-50 rounded-xl p-6 border border-green-200">
                <h3 className="font-bold text-green-900 mb-4 text-lg flex items-center gap-2">
                  <Award className="text-green-600" size={20} />
                  Competitive Advantages
                </h3>
                <ul className="space-y-3">
                  {comparison.strengths.map((strength, index) => (
                    <li key={index} className="text-green-800 flex items-start gap-3">
                      <span className="flex-shrink-0 w-6 h-6 bg-green-600 text-white rounded-full flex items-center justify-center text-xs font-bold mt-0.5">
                        {index + 1}
                      </span>
                      <span className="flex-1">{strength}</span>
                    </li>
                  ))}
                </ul>
              </div>
            )}

            {/* Weaknesses */}
            {comparison.weaknesses && comparison.weaknesses.length > 0 && (
              <div className="bg-gradient-to-br from-orange-50 to-red-50 rounded-xl p-6 border border-orange-200">
                <h3 className="font-bold text-orange-900 mb-4 text-lg flex items-center gap-2">
                  <Target className="text-orange-600" size={20} />
                  Areas for Improvement
                </h3>
                <ul className="space-y-3">
                  {comparison.weaknesses.map((weakness, index) => (
                    <li key={index} className="text-orange-800 flex items-start gap-3">
                      <span className="flex-shrink-0 w-6 h-6 bg-orange-600 text-white rounded-full flex items-center justify-center text-xs font-bold mt-0.5">
                        {index + 1}
                      </span>
                      <span className="flex-1">{weakness}</span>
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </div>
        </div>

        {/* Metrics Table */}
        <div className="bg-white rounded-2xl shadow-lg overflow-hidden mb-6 border border-gray-100">
          <div className="bg-gradient-to-r from-purple-500 to-pink-500 px-6 py-4">
            <h2 className="text-xl font-bold text-white">Performance Metrics</h2>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead className="bg-gray-50">
                <tr>
                  <th className="px-6 py-4 text-left text-sm font-bold text-gray-700">Metric</th>
                  <th className="px-6 py-4 text-center text-sm font-bold text-gray-700">Company</th>
                  <th className="px-6 py-4 text-center text-sm font-bold text-gray-700">Industry Avg</th>
                  <th className="px-6 py-4 text-center text-sm font-bold text-gray-700">Difference</th>
                  <th className="px-6 py-4 text-center text-sm font-bold text-gray-700">Rank</th>
                  <th className="px-6 py-4 text-left text-sm font-bold text-gray-700">Assessment</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {comparison.metricComparisons?.map((metric, index) => (
                  <tr key={index} className="hover:bg-purple-50 transition-colors">
                    <td className="px-6 py-4">
                      <div className="font-semibold text-gray-900">{metric.displayName}</div>
                      <div className="text-xs text-gray-500 mt-1">{metric.unit}</div>
                    </td>
                    <td className="px-6 py-4 text-center">
                      <div className="text-lg font-bold text-blue-600">
                        {metric.targetValue?.toFixed(2)}
                      </div>
                    </td>
                    <td className="px-6 py-4 text-center">
                      <div className="text-lg font-medium text-gray-600">
                        {metric.industryAverage?.toFixed(2)}
                      </div>
                    </td>
                    <td className="px-6 py-4">
                      <div className="flex items-center justify-center gap-2">
                        <span className={`px-3 py-1.5 rounded-full text-sm font-bold ${getDifferenceColor(metric.relativeDifference)} flex items-center gap-1.5`}>
                          {getTrendIcon(metric.relativeDifference)}
                          {metric.relativeDifference > 0 ? '+' : ''}
                          {metric.relativeDifference?.toFixed(1)}%
                        </span>
                      </div>
                    </td>
                    <td className="px-6 py-4 text-center">
                      <span className="inline-flex items-center justify-center w-12 h-12 rounded-full bg-gradient-to-br from-purple-100 to-pink-100 text-purple-900 font-bold text-lg">
                        {metric.rank}
                      </span>
                    </td>
                    <td className="px-6 py-4">
                      <div className="text-sm text-gray-700 leading-relaxed">
                        {metric.assessment}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        {/* Peer Companies */}
        <div className="bg-white rounded-2xl shadow-lg p-6 border border-gray-100">
          <h2 className="text-xl font-bold text-gray-900 mb-6">Peer Companies</h2>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {comparison.peerCompanies?.map((peer, index) => (
              <div key={index} className="bg-gradient-to-br from-gray-50 to-purple-50 border-2 border-purple-200 rounded-xl p-5 hover:shadow-lg transition-all duration-200 hover:scale-105">
                <div className="flex items-center gap-3 mb-3">
                  <div className="w-10 h-10 bg-gradient-to-br from-purple-500 to-pink-500 rounded-lg flex items-center justify-center text-white font-bold">
                    {index + 1}
                  </div>
                  <div>
                    <div className="font-bold text-gray-900">{peer.companyName}</div>
                    <div className="text-sm text-purple-600 font-medium">{peer.companyCode}</div>
                  </div>
                </div>
                <div className="text-xs text-gray-600 bg-white/50 rounded-lg px-3 py-2">
                  {peer.industry}
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
};

export default IndustryComparison;
